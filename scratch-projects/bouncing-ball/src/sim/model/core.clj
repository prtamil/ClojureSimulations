;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.core
  "Immutable lifecycle and controls for the bouncing-ball simulation."
  (:require
   [sim.model.ball :as ball]
   [sim.model.config :as config]))

(def default-seed 2026)
(def dir->vel ball/direction->velocity)
(def advance-axis ball/advance-axis)
(def update-ball ball/advance)

(defn initial-world
  ([]
   (initial-world config/default-config default-seed))
  ([seed-or-config]
   (if (map? seed-or-config)
     (initial-world seed-or-config default-seed)
     (initial-world config/default-config seed-or-config)))
  ([simulation-config seed]
   (let [simulation-config (config/validate! simulation-config)]
     {:config simulation-config
      :seed seed
      :tick 0
      :elapsed-seconds 0.0
      :paused? false
      :speed-mult (get-in simulation-config [:ball :speed-multiplier :initial])
      :theme-index 0
      :sim-fps (get-in simulation-config [:clock :simulation-fps :initial])
      :ball (ball/create simulation-config)})))

(defn- validate-duration! [label seconds non-negative?]
  (when-not (and (number? seconds)
                 (Double/isFinite (double seconds))
                 (if non-negative? (not (neg? seconds)) (pos? seconds)))
    (throw (ex-info (str label " must be "
                         (if non-negative? "non-negative" "positive")
                         " and finite")
                    {:seconds seconds}))))

(defn update-world-steps [world tick-count seconds]
  (when-not (nat-int? tick-count)
    (throw (ex-info "Tick count must be a non-negative integer"
                    {:tick-count tick-count})))
  (validate-duration! "Fixed-step duration" seconds false)
  (if (or (zero? tick-count) (:paused? world))
    world
    (let [elapsed (* tick-count (double seconds))]
      (-> world
          (update :tick + tick-count)
          (update :elapsed-seconds + elapsed)
          (update :ball #(ball/advance (:config world)
                                       (:speed-mult world)
                                       elapsed %))))))

(defn update-world
  ([world]
   (update-world world (/ 1.0 (:sim-fps world))))
  ([world seconds]
   (update-world-steps world 1 seconds)))

(defn project-world
  "Project continuous motion for rendering without changing simulation truth."
  [world seconds]
  (validate-duration! "Projection duration" seconds true)
  (if (or (zero? seconds) (:paused? world))
    world
    (update world :ball #(ball/advance (:config world)
                                       (:speed-mult world)
                                       seconds %))))

(defn toggle-paused [world]
  (update world :paused? not))

(defn adjust-speed [world direction]
  (let [{:keys [min max]} (get-in world [:config :ball :speed-multiplier])]
    (update world :speed-mult
            #(if (pos? direction) (clojure.core/min max (* 2.0 %))
                 (clojure.core/max min (/ % 2.0))))))

(defn adjust-sim-fps [world delta]
  (let [{:keys [min max]} (get-in world [:config :clock :simulation-fps])]
    (update world :sim-fps #(-> (+ % delta) (clojure.core/max min)
                                (clojure.core/min max)))))

(defn reset-world [world]
  (merge (initial-world (:config world) (:seed world))
         (select-keys world [:paused? :speed-mult :theme-index :sim-fps])))
