;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.model.core
  "Immutable lifecycle for a generated tree and its falling leaves."
  (:require
   [leaf-fall.model.config :as config]
   [leaf-fall.model.growth :as growth]
   [leaf-fall.model.leaf :as leaf]
   [leaf-fall.model.raster :as raster]
   [leaf-fall.utility.random :as random]))

(def default-seed 2026)

(defn phase-name [world]
  (name (:phase world)))

(defn cell-at
  "Return one static bark cell from the world's sparse raster."
  [world row column]
  (raster/cell-at (:config world) (:cells world) row column))

(defn- install-tree [world seed cycle]
  (let [seed (random/u32 seed)
        tree (growth/grow-tree (:config world) seed)
        {:keys [cells leaves]} (raster/bake-tree (:config world) tree seed)]
    (assoc world
           :seed seed
           :cycle cycle
           :tree tree
           :cells cells
           :leaves leaves
           :falling-leaves []
           :phase :display
           :phase-elapsed 0.0)))

(defn initial-world
  "Create a complete deterministic leaf-fall world.

  (initial-world)                 ; defaults
  (initial-world 42)              ; defaults with a repeatable tree
  (initial-world custom-config 42)"
  ([]
   (initial-world config/default-config default-seed))
  ([seed-or-config]
   (if (map? seed-or-config)
     (initial-world seed-or-config default-seed)
     (initial-world config/default-config seed-or-config)))
  ([simulation-config seed]
   (let [simulation-config (config/validate! simulation-config)]
     (install-tree
      {:config simulation-config
       :tick 0
       :elapsed-seconds 0.0
       :paused? false
       :sim-fps (get-in simulation-config [:clock :simulation-fps :initial])}
      seed
      1))))

(defn new-tree
  "Generate the next seeded variation and start its display phase."
  [world]
  (let [next-cycle (inc (:cycle world))
        next-seed (random/next-tree-seed (:seed world) next-cycle)]
    (install-tree world next-seed next-cycle)))

(defn toggle-paused [world]
  (update world :paused? not))

(defn adjust-sim-fps [world amount]
  (let [{:keys [min max]} (get-in world [:config :clock :simulation-fps])]
    (update world :sim-fps #(-> (+ % amount) (clojure.core/max min)
                                (clojure.core/min max)))))

(defn- begin-fall [world]
  (assoc world
         :phase :falling
         :phase-elapsed 0.0
         :falling-leaves (mapv leaf/start (:leaves world))))

(defn skip-phase
  "Move to falling immediately, or replace a tree after falling begins."
  [world]
  (case (:phase world)
    :display (begin-fall world)
    (:falling :reset) (new-tree world)
    world))

(defn- advance-falling [world seconds]
  (let [simulation-config (:config world)
        falling-leaves
        (leaf/advance-all (:falling-leaves world)
                          seconds
                          (config/grid-height simulation-config)
                          (get-in simulation-config [:fall :trail-length]))
        world (-> world
                  (assoc :falling-leaves falling-leaves)
                  (update :phase-elapsed + seconds))]
    (if (empty? falling-leaves)
      (assoc world :phase :reset :phase-elapsed 0.0)
      world)))

(defn- advance-one [world seconds]
  (let [world (-> world
                  (update :tick inc)
                  (update :elapsed-seconds + seconds))]
    (case (:phase world)
      :display
      (let [world (update world :phase-elapsed + seconds)]
        (if (>= (:phase-elapsed world)
                (get-in world [:config :phase :display-seconds]))
          (begin-fall world)
          world))

      :falling
      (advance-falling world seconds)

      :reset
      (let [world (update world :phase-elapsed + seconds)]
        (if (>= (:phase-elapsed world)
                (get-in world [:config :phase :reset-seconds]))
          (new-tree world)
          world))

      world)))

(defn- validate-step! [tick-count seconds-per-tick]
  (when-not (nat-int? tick-count)
    (throw (ex-info "Tick count must be a non-negative integer"
                    {:tick-count tick-count})))
  (when-not (and (number? seconds-per-tick)
                 (Double/isFinite (double seconds-per-tick))
                 (pos? seconds-per-tick))
    (throw (ex-info "Fixed-step duration must be positive and finite"
                    {:dt seconds-per-tick}))))

(defn update-world-steps
  "Advance a batch of fixed-duration ticks through the complete phase cycle."
  [world tick-count seconds-per-tick]
  (validate-step! tick-count seconds-per-tick)
  (if (or (:paused? world) (zero? tick-count))
    world
    (loop [world world
           remaining tick-count]
      (if (zero? remaining)
        world
        (recur (advance-one world seconds-per-tick) (dec remaining))))))

(defn update-world
  ([world]
   (update-world world (/ 1.0 (:sim-fps world))))
  ([world seconds-per-tick]
   (update-world-steps world 1 seconds-per-tick)))

(defn active-leaf-count [world]
  (case (:phase world)
    :display (count (:leaves world))
    :falling (count (:falling-leaves world))
    0))

(comment
  (def tree (initial-world 42))
  (select-keys tree [:seed :cycle :phase :tick])
  (count (:leaves tree))
  (def falling (skip-phase tree))
  (def later (update-world-steps falling 120 (/ 1.0 60.0)))
  (select-keys later [:phase :tick])
  (= tree (initial-world 42)))
