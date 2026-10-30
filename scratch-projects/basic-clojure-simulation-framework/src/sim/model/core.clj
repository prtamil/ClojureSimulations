;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.core
  "Own the complete immutable lifecycle of the reference simulation.

  WHY: the example particles have one small motion rule and always advance,
  reset, and disappear with the world, so a separate particle subsystem adds
  more navigation than clarity.

  DATA: empty-world documents the persistent runtime shape; particle maps are
  documented beside their private movement functions.
  OPERATIONS: construct, advance, pause, reset, and change simulation rate.
  BOUNDARY: configuration and validation live in sim.model.config; rendering
  and runtime mutation stay outside the model."
  (:require
   [sim.model.config :as config]))

(def default-seed 2026)

(def empty-clock
  {:tick 0
   :elapsed-seconds 0.0
   :paused? false
   :simulation-fps 0})

(def empty-world
  {:config nil
   :seed 0
   :clock empty-clock
   :theme-index 0
   :particles []})

;; Particle {:id 1 :x 5.0 :y 10.0 :vx 8.0 :vy 0.0}

(defn- wrap [value limit]
  (mod (double value) (double limit)))

(defn- move-particle
  [simulation-config seconds {:keys [x y vx vy] :as particle}]
  (assoc particle
         :x (wrap (+ x (* vx seconds)) (config/grid-width simulation-config))
         :y (wrap (+ y (* vy seconds)) (config/grid-height simulation-config))))

(defn- move-particles [particles simulation-config seconds]
  (mapv #(move-particle simulation-config seconds %) particles))

(defn- start-clock [simulation-config]
  (assoc empty-clock
         :simulation-fps
         (get-in simulation-config [:clock :simulation-fps :initial])))

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
     (assoc empty-world
            :config simulation-config
            :seed seed
            :clock (start-clock simulation-config)
            :particles (get-in simulation-config [:particles :initial])))))

(defn- validate-step! [step-count seconds]
  (when-not (nat-int? step-count)
    (throw (ex-info "Step count must be a non-negative integer"
                    {:step-count step-count})))
  (when-not (and (number? seconds)
                 (Double/isFinite (double seconds))
                 (pos? seconds))
    (throw (ex-info "Fixed-step duration must be a positive finite number"
                    {:dt seconds}))))

(defn advance-world-steps
  "Advance a batch directly because wrapping motion is linear in elapsed time."
  [world step-count seconds]
  (validate-step! step-count seconds)
  (if (or (zero? step-count) (get-in world [:clock :paused?]))
    world
    (let [elapsed-seconds (* step-count (double seconds))]
      (-> world
          (update-in [:clock :tick] + step-count)
          (update-in [:clock :elapsed-seconds] + elapsed-seconds)
          (update :particles move-particles (:config world)
                  elapsed-seconds)))))

(defn advance-world
  "Advance the world by one fixed simulation step."
  ([world]
   (advance-world world
                  (/ 1.0 (get-in world [:clock :simulation-fps]))))
  ([world seconds]
   (advance-world-steps world 1 seconds)))

(defn toggle-paused [world]
  (update-in world [:clock :paused?] not))

(defn change-simulation-fps [world amount]
  (let [{:keys [min max]} (get-in world [:config :clock :simulation-fps])]
    (update-in world [:clock :simulation-fps]
               #(-> (+ % amount)
                    (clojure.core/max min)
                    (clojure.core/min max)))))

(defn reset-world
  "Reset simulation values while preserving interactive controls."
  [world]
  (let [reset (initial-world (:config world) (:seed world))]
    (-> reset
        (assoc :theme-index (:theme-index world))
        (assoc-in [:clock :paused?] (get-in world [:clock :paused?]))
        (assoc-in [:clock :simulation-fps]
                  (get-in world [:clock :simulation-fps])))))
