;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.core
  "Minimal immutable lifecycle for the reference simulation."
  (:require
   [sim.model.config :as config]
   [sim.model.particle :as particle]))

(def default-seed 2026)

;; Kept as a small public convenience for REPL users of the original template.
(def move particle/advance)

(defn initial-world
  "Create an immutable world. A map argument is config; a number is its seed."
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
      :theme-index 0
      :sim-fps (get-in simulation-config [:clock :simulation-fps :initial])
      :particles (get-in simulation-config [:particles :initial])})))

(defn- validate-step! [tick-count seconds]
  (when-not (nat-int? tick-count)
    (throw (ex-info "Tick count must be a non-negative integer"
                    {:tick-count tick-count})))
  (when-not (and (number? seconds)
                 (Double/isFinite (double seconds))
                 (pos? seconds))
    (throw (ex-info "Fixed-step duration must be a positive finite number"
                    {:dt seconds}))))

(defn update-world-steps
  "Advance a batch directly because wrapping motion is linear in elapsed time."
  [world tick-count seconds]
  (validate-step! tick-count seconds)
  (if (or (zero? tick-count) (:paused? world))
    world
    (let [elapsed (* tick-count (double seconds))
          particles (particle/advance-all (:config world)
                                          (:particles world)
                                          elapsed)]
      (-> world
          (update :tick + tick-count)
          (update :elapsed-seconds + elapsed)
          (assoc :particles particles)))))

(defn update-world
  ([world]
   (update-world world (/ 1.0 (:sim-fps world))))
  ([world seconds]
   (update-world-steps world 1 seconds)))

(defn toggle-paused [world]
  (update world :paused? not))

(defn adjust-sim-fps [world delta]
  (let [{:keys [min max]} (get-in world [:config :clock :simulation-fps])]
    (update world :sim-fps #(-> (+ % delta) (clojure.core/max min)
                                (clojure.core/min max)))))

(defn reset-world
  "Reset simulation values while preserving interactive controls."
  [world]
  (merge (initial-world (:config world) (:seed world))
         (select-keys world [:paused? :theme-index :sim-fps])))
