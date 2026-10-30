;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.model.core
  "Own the complete, pure lifecycle of a leaf-fall world.

  WHY: construction, phase changes, time, controls, and reseeding form one
  domain story.
  DATA: empty-world documents the complete persistent runtime shape.
  OPERATIONS: calculate, rasterize, construct, advance phases, and start trees.
  BOUNDARY: growth owns continuous tree geometry; raster owns discrete tree
  placement; leaf owns fall motion; rendering and runtime mutation stay out."
  (:require
   [leaf-fall.model.config :as config]
   [leaf-fall.model.growth :as growth]
   [leaf-fall.model.leaf :as leaf]
   [leaf-fall.model.raster :as raster]
   [leaf-fall.utility.random :as random]))

(def default-seed 2026)

(def empty-clock
  {:tick 0
   :elapsed-seconds 0.0
   :paused? false
   :simulation-fps 0})

(def empty-world
  {:config nil
   :seed nil
   :cycle 0
   :clock empty-clock
   :tree growth/empty-tree
   :cells {}
   :leaf-templates []
   :falling-leaves []
   :phase :display
   :phase-elapsed 0.0})

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

(defn rasterize-tree
  "Convert tree geometry into grid cells and immutable leaf templates."
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
      rasterize-tree))

(defn cell-at
  [world row column]
  (raster/cell-at world row column))

;; World and cycle creation

(defn- new-world
  [simulation-config]
  (assoc empty-world
         :config simulation-config
         :clock (assoc empty-clock
                       :simulation-fps
                       (get-in simulation-config
                               [:clock :simulation-fps :initial]))))

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
      (update-in [:clock :tick] inc)
      (update-in [:clock :elapsed-seconds] + seconds)
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

(defn advance-world-steps
  "Advance several fixed-duration ticks through the phase cycle."
  [world tick-count seconds-per-tick]
  (validate-step! tick-count seconds-per-tick)
  (if (or (get-in world [:clock :paused?]) (zero? tick-count))
    world
    (loop [world world
           remaining tick-count]
      (if (zero? remaining)
        world
        (recur (advance-one-step world seconds-per-tick)
               (dec remaining))))))

(defn advance-world
  ([world]
   (advance-world world
                  (/ 1.0 (get-in world [:clock :simulation-fps]))))
  ([world seconds-per-tick]
   (advance-world-steps world 1 seconds-per-tick)))

;; User controls

(defn- clamp
  [value minimum maximum]
  (min maximum (max minimum value)))

(defn toggle-paused
  [world]
  (update-in world [:clock :paused?] not))

(defn change-simulation-fps
  [world amount]
  (let [{:keys [min max]} (get-in world [:config :clock :simulation-fps])]
    (update-in world [:clock :simulation-fps]
               #(clamp (+ % amount) min max))))

(defn active-leaf-count
  [world]
  (case (:phase world)
    :display (count (:leaf-templates world))
    :falling (count (:falling-leaves world))
    0))

(comment
  (def world (initial-world 42))
  (select-keys world [:seed :cycle :phase])
  (:clock world)
  (count (:leaf-templates world))
  (def falling (skip-phase world))
  (def later (advance-world-steps falling 120 (/ 1.0 60.0)))
  (select-keys later [:phase])
  (:clock later)
  (= world (initial-world 42)))
