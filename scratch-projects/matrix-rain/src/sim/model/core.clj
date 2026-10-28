;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.core
  "Immutable lifecycle and controls for deterministic digital rain."
  (:require
   [sim.model.config :as config]
   [sim.model.drop :as drop]))

(def default-seed 2026)
(def spawn-drop drop/spawn)
(def update-drop drop/advance)

(defn initial-world
  ([]
   (initial-world config/default-config default-seed))
  ([seed-or-config]
   (if (map? seed-or-config)
     (initial-world seed-or-config default-seed)
     (initial-world config/default-config seed-or-config)))
  ([simulation-config seed]
   (let [simulation-config (config/validate! simulation-config)
         [rng-state drops] (drop/spawn-many simulation-config (long seed))]
     {:config simulation-config
      :seed seed
      :rng-state rng-state
      :tick 0
      :elapsed-seconds 0.0
      :paused? false
      :speed-mult (get-in simulation-config [:rain :speed-multiplier :initial])
      :theme-index 0
      :sim-fps (get-in simulation-config [:clock :simulation-fps :initial])
      :drops drops})))

(defn- step [world seconds]
  (let [[rng-state drops]
        (drop/advance-all (:config world) (:speed-mult world) seconds
                          (:rng-state world) (:drops world))]
    (-> world
        (update :tick inc)
        (update :elapsed-seconds + seconds)
        (assoc :rng-state rng-state :drops drops))))

(defn- validate-step! [tick-count seconds]
  (when-not (nat-int? tick-count)
    (throw (ex-info "Tick count must be a non-negative integer"
                    {:tick-count tick-count})))
  (when-not (and (number? seconds)
                 (Double/isFinite (double seconds))
                 (pos? seconds))
    (throw (ex-info "Fixed-step duration must be a positive finite number"
                    {:dt seconds}))))

(defn update-world-steps [world tick-count seconds]
  (validate-step! tick-count seconds)
  (if (or (zero? tick-count) (:paused? world))
    world
    (nth (iterate #(step % seconds) world) tick-count)))

(defn update-world
  ([world]
   (update-world world (/ 1.0 (:sim-fps world))))
  ([world seconds]
   (update-world-steps world 1 seconds)))

(defn toggle-paused [world]
  (update world :paused? not))

(defn adjust-speed [world direction]
  (let [{:keys [min max]} (get-in world [:config :rain :speed-multiplier])]
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
