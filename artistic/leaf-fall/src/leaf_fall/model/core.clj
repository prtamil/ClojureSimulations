;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.model.core
  "The complete, pure story of constructing and animating a leaf-fall world."
  (:require
   [leaf-fall.model.config :as config]
   [leaf-fall.model.growth :as growth]
   [leaf-fall.model.leaf :as leaf]
   [leaf-fall.model.raster :as raster]
   [leaf-fall.utility.random :as random]))

(def default-seed 2026)

;; Tree construction

(defn- construction-settings
  [world]
  {:config (:config world)
   :seed (:seed world)})

(defn calculate-tree
  "Calculate trunk and crown geometry for a world."
  [world]
  (let [settings (construction-settings world)
        trunk (growth/calculate-trunk settings)
        crown (growth/calculate-crown settings trunk)]
    {:trunk trunk
     :crown crown}))

(defn place-tree
  "Place tree geometry and scatter its leaf templates."
  [world]
  (let [scene (-> raster/empty-scene
                  (raster/place-trunk world)
                  (raster/place-crown world)
                  (raster/scatter-leaves world))]
    (merge world scene)))

(defn construct-tree
  "Calculate and place a complete tree."
  [world]
  (-> world
      (assoc :tree (calculate-tree world))
      place-tree))

(defn cell-at
  [world row column]
  (raster/cell-at world row column))

;; World and cycle creation

(defn- new-world
  [simulation-config]
  (merge
   {:config simulation-config
    :seed nil
    :cycle 0
    :tree growth/empty-tree
    :falling-leaves []
    :phase :display
    :phase-elapsed 0.0
    :tick 0
    :elapsed-seconds 0.0
    :paused? false
    :sim-fps (get-in simulation-config [:clock :simulation-fps :initial])}
   raster/empty-scene))

(defn- start-tree-cycle
  [world seed cycle]
  (-> world
      (assoc :seed (random/u32 seed)
             :cycle cycle)
      construct-tree
      (assoc :falling-leaves []
             :phase :display
             :phase-elapsed 0.0)))

(defn initial-world
  "Create a complete deterministic leaf-fall world."
  ([]
   (initial-world config/default-config default-seed))
  ([seed-or-config]
   (if (map? seed-or-config)
     (initial-world seed-or-config default-seed)
     (initial-world config/default-config seed-or-config)))
  ([simulation-config seed]
   (-> simulation-config
       config/validate!
       new-world
       (start-tree-cycle seed 1))))

(defn new-tree
  "Generate the next seeded variation and begin its display phase."
  [world]
  (let [next-cycle (inc (:cycle world))
        next-seed (random/next-tree-seed (:seed world) next-cycle)]
    (start-tree-cycle world next-seed next-cycle)))

;; Phase lifecycle

(defn phase-name
  [world]
  (name (:phase world)))

(defn- start-fall
  [world]
  (assoc world
         :phase :falling
         :phase-elapsed 0.0
         :falling-leaves (mapv leaf/start (:leaf-templates world))))

(defn skip-phase
  "Start falling immediately, or replace a tree after falling begins."
  [world]
  (case (:phase world)
    :display (start-fall world)
    (:falling :reset) (new-tree world)
    world))

(defn- advance-display
  [world seconds]
  (let [world (update world :phase-elapsed + seconds)
        display-seconds (get-in world [:config :phase :display-seconds])]
    (if (>= (:phase-elapsed world) display-seconds)
      (start-fall world)
      world)))

(defn- advance-falling
  [world seconds]
  (let [simulation-config (:config world)
        falling-leaves
        (leaf/advance-leaves
         (:falling-leaves world)
         seconds
         (config/grid-height simulation-config)
         (get-in simulation-config [:fall :trail-length]))
        world (-> world
                  (assoc :falling-leaves falling-leaves)
                  (update :phase-elapsed + seconds))]
    (if (empty? falling-leaves)
      (assoc world :phase :reset :phase-elapsed 0.0)
      world)))

(defn- advance-reset
  [world seconds]
  (let [world (update world :phase-elapsed + seconds)
        reset-seconds (get-in world [:config :phase :reset-seconds])]
    (if (>= (:phase-elapsed world) reset-seconds)
      (new-tree world)
      world)))

(defn- advance-phase
  [world seconds]
  (case (:phase world)
    :display (advance-display world seconds)
    :falling (advance-falling world seconds)
    :reset (advance-reset world seconds)
    world))

(defn- advance-one-step
  [world seconds]
  (-> world
      (update :tick inc)
      (update :elapsed-seconds + seconds)
      (advance-phase seconds)))

;; Time

(defn- validate-step!
  [tick-count seconds-per-tick]
  (when-not (nat-int? tick-count)
    (throw (ex-info "Tick count must be a non-negative integer"
                    {:tick-count tick-count})))
  (when-not (and (number? seconds-per-tick)
                 (Double/isFinite (double seconds-per-tick))
                 (pos? seconds-per-tick))
    (throw (ex-info "Fixed-step duration must be positive and finite"
                    {:dt seconds-per-tick}))))

(defn update-world-steps
  "Advance several fixed-duration ticks through the phase cycle."
  [world tick-count seconds-per-tick]
  (validate-step! tick-count seconds-per-tick)
  (if (or (:paused? world) (zero? tick-count))
    world
    (loop [world world
           remaining tick-count]
      (if (zero? remaining)
        world
        (recur (advance-one-step world seconds-per-tick)
               (dec remaining))))))

(defn update-world
  ([world]
   (update-world world (/ 1.0 (:sim-fps world))))
  ([world seconds-per-tick]
   (update-world-steps world 1 seconds-per-tick)))

;; User controls

(defn- clamp
  [value minimum maximum]
  (min maximum (max minimum value)))

(defn toggle-paused
  [world]
  (update world :paused? not))

(defn adjust-sim-fps
  [world amount]
  (let [{:keys [min max]} (get-in world [:config :clock :simulation-fps])]
    (update world :sim-fps #(clamp (+ % amount) min max))))

(defn active-leaf-count
  [world]
  (case (:phase world)
    :display (count (:leaf-templates world))
    :falling (count (:falling-leaves world))
    0))

(comment
  (def world (initial-world 42))
  (select-keys world [:seed :cycle :phase :tick])
  (count (:leaf-templates world))
  (def falling (skip-phase world))
  (def later (update-world-steps falling 120 (/ 1.0 60.0)))
  (select-keys later [:phase :tick])
  (= world (initial-world 42)))
