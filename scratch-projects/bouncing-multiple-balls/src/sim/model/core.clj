;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.core
  "Immutable orchestration for wall motion and pairwise ball collisions."
  (:require
   [sim.model.ball :as ball]
   [sim.model.collision :as collision]
   [sim.model.config :as config]))

(def default-seed 2026)
(def balls-overlap? ball/overlap?)
(def advance-axis ball/advance-axis)
(def resolve-ball-collision collision/resolve-pair)

(defn- collision-pass [simulation-config balls]
  (let [restitution (get-in simulation-config [:physics :restitution])]
    (->> (collision/resolve-all restitution balls)
         (mapv #(ball/confine simulation-config %)))))

(defn- substep [world balls seconds]
  (let [simulation-config (:config world)
        moved (mapv #(ball/advance simulation-config (:speed-mult world)
                                   seconds %)
                    balls)
        passes (get-in simulation-config [:physics :collision-iterations])]
    (nth (iterate #(collision-pass simulation-config %) moved) passes)))

(defn- tick [world balls seconds]
  (let [substeps (get-in world [:config :physics :substeps])
        substep-seconds (/ (double seconds) substeps)]
    (nth (iterate #(substep world % substep-seconds) balls) substeps)))

(defn initial-world
  ([]
   (initial-world config/default-config default-seed))
  ([seed-or-config]
   (if (map? seed-or-config)
     (initial-world seed-or-config default-seed)
     (initial-world config/default-config seed-or-config)))
  ([simulation-config seed]
   (let [simulation-config (config/validate! simulation-config)
         [rng-state balls] (ball/spawn-many simulation-config (long seed))]
     {:config simulation-config
      :seed seed
      :rng-state rng-state
      :tick 0
      :elapsed-seconds 0.0
      :paused? false
      :speed-mult (get-in simulation-config [:balls :speed-multiplier :initial])
      :theme-index 0
      :sim-fps (get-in simulation-config [:clock :simulation-fps :initial])
      :balls balls})))

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
    (assoc world
           :tick (+ (:tick world) tick-count)
           :elapsed-seconds (+ (:elapsed-seconds world)
                               (* tick-count (double seconds)))
           :balls (nth (iterate #(tick world % seconds) (:balls world))
                       tick-count))))

(defn update-world
  ([world]
   (update-world world (/ 1.0 (:sim-fps world))))
  ([world seconds]
   (update-world-steps world 1 seconds)))

(defn toggle-paused [world]
  (update world :paused? not))

(defn adjust-speed [world direction]
  (let [{:keys [min max]} (get-in world [:config :balls :speed-multiplier])]
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
