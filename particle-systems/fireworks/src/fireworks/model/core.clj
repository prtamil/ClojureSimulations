;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.model.core
  "Pure orchestration for an immutable fireworks world.

  The world contains only active domain values. Rocket and spark transitions
  live in focused namespaces; renderers consume a separate cell projection."
  (:require
   [fireworks.model.config :as config]
   [fireworks.model.rocket :as rocket]
   [fireworks.utility.random :as random]
   [fireworks.utility.theme :as theme]))

(def default-seed 2026)

(defn rocket-count [world]
  (count (:rockets world)))

(defn- new-show [simulation-config rocket-count]
  {:rockets (rocket/initial-rockets simulation-config rocket-count)
   :next-rocket-id rocket-count})

(defn initial-world
  "Create a deterministic world from validated config and an explicit seed."
  ([]
   (initial-world config/default-config default-seed))
  ([seed-or-config]
   (if (map? seed-or-config)
     (initial-world seed-or-config default-seed)
     (initial-world config/default-config seed-or-config)))
  ([simulation-config seed]
   (let [simulation-config (config/validate! simulation-config)
         rocket-count (get-in simulation-config [:rockets :count :initial])]
     (merge
      {:config simulation-config
       :seed (random/u32 seed)
       :rng-state (random/u32 seed)
       :tick 0
       :elapsed-seconds 0.0
       :paused? false
       :theme-index 0
       :sim-fps (get-in simulation-config [:clock :simulation-fps :initial])}
      (new-show simulation-config rocket-count)))))

(defn- validate-step! [tick-count dt]
  (when-not (nat-int? tick-count)
    (throw (ex-info "Tick count must be a non-negative integer"
                    {:tick-count tick-count})))
  (when-not (and (number? dt)
                 (Double/isFinite (double dt))
                 (pos? dt))
    (throw (ex-info "Fixed-step duration must be positive and finite"
                    {:dt dt}))))

(defn- advance-rockets [simulation-config rng dt rockets]
  (reduce
   (fn [[rng advanced] current]
     (let [[rng next-rocket]
           (rocket/advance simulation-config rng dt current)]
       [rng (conj advanced next-rocket)]))
   [rng []]
   rockets))

(defn- advance-once [world dt]
  (let [[rng rockets]
        (advance-rockets (:config world) (:rng-state world) dt
                         (:rockets world))]
    (-> world
        (assoc :rng-state rng :rockets rockets)
        (update :tick inc)
        (update :elapsed-seconds + dt))))

(defn update-world-steps
  "Advance a running world through fixed-duration simulation steps."
  [world tick-count dt]
  (validate-step! tick-count dt)
  (if (or (:paused? world) (zero? tick-count))
    world
    (loop [world world
           remaining tick-count]
      (if (zero? remaining)
        world
        (recur (advance-once world dt) (dec remaining))))))

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

(defn cycle-theme [world direction]
  (update world :theme-index #(theme/cycle-index % direction)))

(defn- add-rocket [world]
  (let [id (:next-rocket-id world)
        delay (get-in world [:config :rockets :new-launch-delay-seconds])]
    (-> world
        (update :rockets conj (rocket/waiting id delay))
        (update :next-rocket-id inc))))

(defn adjust-rocket-count
  "Grow or shrink the vector of active rockets within configured bounds."
  [world amount]
  (let [{:keys [min max]} (get-in world [:config :rockets :count])
        current (rocket-count world)
        target (clojure.core/min max
                                 (clojure.core/max min (+ current amount)))]
    (cond
      (> target current)
      (nth (iterate add-rocket world) (- target current))

      (< target current)
      (update world :rockets #(vec (take target %)))

      :else world)))

(defn reset-show
  "Restart from the original seed while preserving interactive settings."
  [world]
  (merge world
         {:rng-state (:seed world)
          :tick 0
          :elapsed-seconds 0.0
          :paused? false}
         (new-show (:config world) (rocket-count world))))

(defn resize-world
  "Resize and restart scene geometry while preserving runtime preferences."
  [world width height]
  (let [width (max 4 (int width))
        height (max 4 (int height))]
    (if (and (= width (config/grid-width (:config world)))
             (= height (config/grid-height (:config world))))
      world
      (let [simulation-config (-> (:config world)
                                  (assoc-in [:grid :width] width)
                                  (assoc-in [:grid :height] height)
                                  config/validate!)]
        (merge (assoc world :config simulation-config)
               (new-show simulation-config (rocket-count world)))))))
