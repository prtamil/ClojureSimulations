;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.model.core
  "Own the complete, pure lifecycle of a bonsai world.

  WHY: construction, time, controls, style/theme changes, and reseeding form
  one domain story.
  DATA: empty-world documents the complete persistent runtime shape.
  OPERATIONS: calculate, rasterize, construct, advance, and apply user actions.
  BOUNDARY: growth owns continuous tree geometry; raster owns discrete grid
  placement; configuration, rendering, and runtime mutation remain outside."
  (:require
   [bonsai.model.config :as config]
   [bonsai.model.growth :as growth]
   [bonsai.model.raster :as raster]
   [bonsai.utility.random :as random]
   [bonsai.utility.theme :as theme]))

(def default-seed 2026)

(def empty-clock
  {:tick 0
   :elapsed-seconds 0.0
   :paused? false
   :simulation-fps 0})

(def empty-world
  {:config nil
   :seed 0
   :generation 0
   :clock empty-clock
   :style-index 1
   :theme-index 0
   :pot growth/empty-pot
   :tree growth/empty-tree
   :cells {}
   :wind 0.0
   :growth-progress 1.0})

;; Tree construction

(defn current-style-key [world]
  (nth config/style-order (:style-index world)))

(defn current-style [world]
  (config/style (current-style-key world)))

(defn- construction-context
  [world]
  {:config (:config world)
   :seed (:seed world)
   :style-key (current-style-key world)
   :style (current-style world)})

(defn calculate-tree
  "Calculate the pot, trunk, and crown for a world."
  [world]
  (let [context (construction-context world)
        pot (growth/calculate-pot context)
        trunk (growth/calculate-trunk context pot)
        crown (growth/calculate-crown context trunk)]
    {:pot pot
     :tree {:trunk trunk
            :crown crown}}))

(defn rasterize-tree
  "Convert existing geometry into themed cells at discrete grid positions."
  [world]
  (assoc world :cells
         (-> {}
             (raster/place-trunk world)
             (raster/place-crown world)
             (raster/place-pot world))))

(defn construct-tree
  "Calculate and place a complete tree."
  [world]
  (let [{:keys [pot tree]} (calculate-tree world)]
    (-> world
        (assoc :pot pot :tree tree)
        rasterize-tree)))

(defn cell-at
  [world row column]
  (raster/cell-at world row column))

;; World creation

(defn- new-world
  [simulation-config seed]
  (assoc empty-world
         :config simulation-config
         :seed (random/u32 seed)
         :clock (assoc empty-clock
                       :simulation-fps
                       (get-in simulation-config
                               [:clock :simulation-fps :initial]))
         :wind (get-in simulation-config [:animation :wind-initial])))

(defn initial-world
  "Create a complete deterministic bonsai world."
  ([]
   (initial-world config/default-config default-seed))
  ([seed-or-config]
   (if (map? seed-or-config)
     (initial-world seed-or-config default-seed)
     (initial-world config/default-config seed-or-config)))
  ([simulation-config seed]
   (-> simulation-config
       config/validate!
       (new-world seed)
       construct-tree)))

;; Time and growth

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

(defn- advance-clock
  [world tick-count elapsed-seconds]
  (-> world
      (update-in [:clock :tick] + tick-count)
      (update-in [:clock :elapsed-seconds] + elapsed-seconds)))

(defn- advance-growth
  [world elapsed-seconds]
  (let [growth-seconds (get-in world [:config :animation :growth-seconds])]
    (update world :growth-progress
            #(min 1.0 (+ % (/ elapsed-seconds growth-seconds))))))

(defn advance-world-steps
  "Advance the clock and growth animation by several fixed steps."
  [world tick-count seconds-per-tick]
  (validate-step! tick-count seconds-per-tick)
  (if (or (get-in world [:clock :paused?]) (zero? tick-count))
    world
    (let [elapsed-seconds (* tick-count (double seconds-per-tick))]
      (-> world
          (advance-clock tick-count elapsed-seconds)
          (advance-growth elapsed-seconds)))))

(defn advance-world
  ([world]
   (advance-world world
                  (/ 1.0 (get-in world [:clock :simulation-fps]))))
  ([world seconds-per-tick]
   (advance-world-steps world 1 seconds-per-tick)))

;; User actions

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

(defn adjust-wind
  [world amount]
  (let [{:keys [wind-min wind-max]} (get-in world [:config :animation])]
    (update world :wind #(clamp (+ % amount) wind-min wind-max))))

(defn- select-style
  [world direction]
  (update world :style-index
          #(mod (+ % direction) (count config/style-order))))

(defn- select-theme
  [world direction]
  (update world :theme-index #(theme/cycle-index % direction)))

(defn- restart-growth
  [world]
  (assoc world :growth-progress 0.0))

(defn cycle-style
  [world direction]
  (-> world
      (select-style direction)
      restart-growth
      construct-tree))

(defn cycle-theme
  "Change the presentation without recalculating the tree."
  [world direction]
  (-> world
      (select-theme direction)
      rasterize-tree))

(defn- choose-next-seed
  [world]
  (let [[_ seed] (random/next-u32
                  (bit-xor (long (:seed world)) 0xA5A5A5A5))]
    (assoc world :seed seed)))

(defn reseed
  [world]
  (-> world
      choose-next-seed
      (update :generation inc)
      restart-growth
      construct-tree))

(defn replay-growth
  [world]
  (restart-growth world))
