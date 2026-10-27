;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
;;
;; Model rule: every function accepts data and returns data. Test transitions
;; here without atoms, printing, Quil, or sleeps.
(ns sim.model
  (:require
   [sim.config :as config]
   [sim.random :as random]))

(def default-seed 2026)

(defn- wrap [value limit]
  (mod (double value) (double limit)))

(defn move
  "Move one particle by dt seconds and wrap it at the configured grid edges."
  [simulation-config dt {:keys [x y vx vy] :as particle}]
  (assoc particle
         :x (wrap (+ x (* vx dt)) (config/grid-width simulation-config))
         :y (wrap (+ y (* vy dt)) (config/grid-height simulation-config))))

(defn initial-world
  "Create a deterministic immutable world.

  With one argument, a map is treated as config and a number as the seed."
  ([]
   (initial-world config/default-config default-seed))
  ([seed-or-config]
   (if (map? seed-or-config)
     (initial-world seed-or-config default-seed)
     (initial-world config/default-config seed-or-config)))
  ([simulation-config seed]
   (let [simulation-config (config/validate! simulation-config)
         [rng-state permutation] (random/shuffled-permutation seed)]
     {:config simulation-config
      :seed seed
      :rng-state rng-state
      :noise-permutation permutation
      :tick 0
      :elapsed-seconds 0.0
      :paused? false
      :theme-index 0
      :sim-fps (get-in simulation-config [:clock :simulation-fps :initial])
      :particles (get-in simulation-config [:particles :initial])})))

(defn update-world-steps
  "Advance the model by a batch of fixed-duration simulation ticks."
  [world tick-count dt]
  (when-not (nat-int? tick-count)
    (throw (ex-info "Tick count must be a non-negative integer"
                    {:tick-count tick-count})))
  (when-not (and (number? dt)
                 (Double/isFinite (double dt))
                 (pos? dt))
    (throw (ex-info "Fixed-step duration must be a positive finite number"
                    {:dt dt})))
  (if (or (zero? tick-count) (:paused? world))
    world
    (let [elapsed (* tick-count (double dt))
          simulation-config (:config world)]
      (-> world
          (update :tick + tick-count)
          (update :elapsed-seconds + elapsed)
          (update :particles
                  #(mapv (partial move simulation-config elapsed) %))))))

(defn update-world
  "Advance the world by one fixed simulation step."
  ([world]
   (update-world world (/ 1.0 (:sim-fps world))))
  ([world dt]
   (update-world-steps world 1 dt)))

(defn toggle-paused [world]
  (update world :paused? not))

(defn adjust-sim-fps [world delta]
  (let [{:keys [min max]} (get-in world [:config :clock :simulation-fps])]
    (update world :sim-fps
            #(clojure.core/min max (clojure.core/max min (+ % delta))))))

(defn reset-world
  "Reset simulation data while preserving user-facing runtime controls."
  [world]
  (let [fresh (initial-world (:config world) (:seed world))]
    (assoc fresh
           :paused? (:paused? world)
           :theme-index (:theme-index world)
           :sim-fps (:sim-fps world))))

(comment
  ;; MODEL GUIDE FOR A REPL PROMPT
  ;; Copy/evaluate this require first. Use :reload after editing model.clj.
  (require '[sim.model :as model] :reload)

  ;; 1. Construct a known starting value and inspect only useful keys.
  (def world-0 (model/initial-world 42))
  (select-keys world-0
               [:seed :tick :elapsed-seconds :paused? :sim-fps :theme-index])
  (:particles world-0)

  ;; 2. Exercise one transition. Keep both values; worlds are immutable.
  (def world-1 (model/update-world world-0))
  (:tick world-0) ; => 0
  (:tick world-1) ; => 1
  (= world-0 world-1) ; => false

  ;; 3. Exercise a fixed-step batch exactly as the runtime clock does.
  (def world-60
    (model/update-world-steps world-0 60 (/ 1.0 60.0)))
  (select-keys world-60 [:tick :elapsed-seconds])

  ;; 4. Make manual checks executable. A failed assertion throws immediately.
  (assert (= 60 (:tick world-60)))
  (assert (< (Math/abs (- 1.0 (:elapsed-seconds world-60))) 1.0e-12))
  (assert (= world-0 (model/initial-world 42))) ; deterministic seed

  ;; 5. Test a specific edge case by modifying data, not production globals.
  (def edge-world
    (assoc world-0
           :particles [{:id 1 :x 39.0 :y 19.0 :vx 4.0 :vy 3.0}]))
  (def wrapped-world (model/update-world edge-world 0.5))
  (-> wrapped-world :particles first (select-keys [:x :y])) ; => {:x 1.0 :y 0.5}

  ;; 6. Verify pause/reset behavior as ordinary values.
  (def paused-world (model/toggle-paused world-0))
  (= paused-world (model/update-world-steps paused-world 10 0.1)) ; => true
  (:tick (model/reset-world world-60)) ; => 0

  ;; 7. Run the real model test namespace from a development REPL. The :dev
  ;; alias includes the test directory, so these forms work at the prompt.
  (require '[clojure.test :as test]
           '[sim.model-test]
           :reload)
  (test/run-tests 'sim.model-test)
  )
