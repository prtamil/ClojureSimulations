;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.model.core
  "Pure orchestration for tree geometry and time-based growth state."
  (:require
   [bonsai.model.config :as config]
   [bonsai.model.growth :as growth]
   [bonsai.model.raster :as raster]
   [bonsai.utility.random :as random]
   [bonsai.utility.theme :as theme]))

(def default-seed 2026)

(defn current-style-key [world]
  (nth config/style-order (:style-index world)))

(defn current-style [world]
  (config/style (current-style-key world)))

(defn- bake-cells [world]
  (raster/bake-tree (:config world)
                    (:tree world)
                    (:pot-top world)
                    (theme/current-theme world)
                    (:seed world)))

(defn rebuild-presentation
  "Reproject existing geometry for the selected seasonal theme."
  [world]
  (assoc world :cells (bake-cells world)))

(defn rebuild-scene
  "Regenerate tree geometry from config, seed, and selected style."
  [world]
  (let [{:keys [tree pot-top]}
        (growth/grow-tree (:config world) (:seed world)
                          (current-style-key world))]
    (-> world
        (assoc :tree tree :pot-top pot-top)
        rebuild-presentation)))

(defn cell-at [world row column]
  (raster/cell-at (:config world) (:cells world) row column))

(defn initial-world
  "Create a complete deterministic bonsai world."
  ([]
   (initial-world config/default-config default-seed))
  ([seed-or-config]
   (if (map? seed-or-config)
     (initial-world seed-or-config default-seed)
     (initial-world config/default-config seed-or-config)))
  ([simulation-config seed]
   (let [simulation-config (config/validate! simulation-config)]
     (rebuild-scene
      {:config simulation-config
       :seed (random/u32 seed)
       :generation 0
       :tick 0
       :elapsed-seconds 0.0
       :paused? false
       :style-index 1
       :theme-index 0
       :sim-fps (get-in simulation-config [:clock :simulation-fps :initial])
       :wind (get-in simulation-config [:animation :wind-initial])
       :growth-progress 1.0}))))

(defn- validate-step! [tick-count dt]
  (when-not (nat-int? tick-count)
    (throw (ex-info "Tick count must be a non-negative integer"
                    {:tick-count tick-count})))
  (when-not (and (number? dt)
                 (Double/isFinite (double dt))
                 (pos? dt))
    (throw (ex-info "Fixed-step duration must be positive and finite"
                    {:dt dt}))))

(defn update-world-steps
  "Advance elapsed time and the optional growth reveal."
  [world tick-count dt]
  (validate-step! tick-count dt)
  (if (or (:paused? world) (zero? tick-count))
    world
    (let [elapsed (* tick-count (double dt))
          growth-seconds (get-in world [:config :animation :growth-seconds])]
      (-> world
          (update :tick + tick-count)
          (update :elapsed-seconds + elapsed)
          (update :growth-progress
                  #(min 1.0 (+ % (/ elapsed growth-seconds))))))))

(defn update-world
  ([world]
   (update-world world (/ 1.0 (:sim-fps world))))
  ([world dt]
   (update-world-steps world 1 dt)))

(defn toggle-paused [world]
  (update world :paused? not))

(defn adjust-sim-fps [world amount]
  (let [{:keys [min max]} (get-in world [:config :clock :simulation-fps])]
    (update world :sim-fps #(clojure.core/min max
                                              (clojure.core/max min (+ % amount))))))

(defn adjust-wind [world amount]
  (let [{:keys [wind-min wind-max]} (get-in world [:config :animation])]
    (update world :wind #(min wind-max (max wind-min (+ % amount))))))

(defn cycle-style [world direction]
  (-> world
      (update :style-index
              #(mod (+ % direction) (count config/style-order)))
      (assoc :growth-progress 0.0)
      rebuild-scene))

(defn cycle-theme
  "Change seasonal presentation without changing the generated tree."
  [world direction]
  (-> world
      (update :theme-index #(theme/cycle-index % direction))
      rebuild-presentation))

(defn reseed [world]
  (let [[_ next-seed]
        (random/next-u32 (bit-xor (long (:seed world)) 0xA5A5A5A5))]
    (-> world
        (assoc :seed next-seed :growth-progress 0.0)
        (update :generation inc)
        rebuild-scene)))

(defn replay-growth [world]
  (assoc world :growth-progress 0.0))
