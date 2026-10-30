;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.core
  "Own the immutable lifecycle of deterministic digital rain.

  WHY: construction, time, controls, reset, and stream advancement form one
  readable world workflow.
  DATA: empty-world documents the complete persistent runtime shape.
  OPERATIONS: construct, advance, pause, reset, and change controls.
  BOUNDARY: sim.model.drop owns stream creation/evolution; projection owns
  visibility; configuration, rendering, and runtime mutation stay outside."
  (:require
   [sim.model.config :as config]
   [sim.model.drop :as drop]))

(def default-seed 2026)

(def empty-clock
  {:tick 0
   :elapsed-seconds 0.0
   :paused? false
   :simulation-fps 0})

(def empty-world
  {:config nil
   :seed 0
   :rng-state 0
   :clock empty-clock
   :speed-multiplier 1.0
   :theme-index 0
   :drops []})

(defn- start-clock [simulation-config]
  (assoc empty-clock
         :simulation-fps
         (get-in simulation-config [:clock :simulation-fps :initial])))

(defn initial-world
  "Create a deterministic world. A map argument is config; a number is seed."
  ([]
   (initial-world config/default-config default-seed))
  ([seed-or-config]
   (if (map? seed-or-config)
     (initial-world seed-or-config default-seed)
     (initial-world config/default-config seed-or-config)))
  ([simulation-config seed]
   (let [simulation-config (config/validate! simulation-config)
         [rng-state drops] (drop/spawn-many simulation-config (long seed))]
     (assoc empty-world
            :config simulation-config
            :seed seed
            :rng-state rng-state
            :clock (start-clock simulation-config)
            :speed-multiplier
            (get-in simulation-config [:rain :speed-multiplier :initial])
            :drops drops))))

(defn- advance-one-step [world seconds]
  (let [[rng-state drops]
        (drop/advance-all (:config world)
                          (:speed-multiplier world)
                          seconds
                          (:rng-state world)
                          (:drops world))]
    (-> world
        (update-in [:clock :tick] inc)
        (update-in [:clock :elapsed-seconds] + seconds)
        (assoc :rng-state rng-state :drops drops))))

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
  "Advance a batch as sequential deterministic fixed steps."
  [world step-count seconds]
  (validate-step! step-count seconds)
  (if (or (zero? step-count) (get-in world [:clock :paused?]))
    world
    (nth (iterate #(advance-one-step % seconds) world) step-count)))

(defn advance-world
  "Advance the world by one fixed simulation step."
  ([world]
   (advance-world world
                  (/ 1.0 (get-in world [:clock :simulation-fps]))))
  ([world seconds]
   (advance-world-steps world 1 seconds)))

(defn toggle-paused [world]
  (update-in world [:clock :paused?] not))

(defn change-speed [world direction]
  (let [{:keys [min max]} (get-in world [:config :rain :speed-multiplier])]
    (update world :speed-multiplier
            #(if (pos? direction)
               (clojure.core/min max (* 2.0 %))
               (clojure.core/max min (/ % 2.0))))))

(defn change-simulation-fps [world amount]
  (let [{:keys [min max]} (get-in world [:config :clock :simulation-fps])]
    (update-in world [:clock :simulation-fps]
               #(-> (+ % amount)
                    (clojure.core/max min)
                    (clojure.core/min max)))))

(defn reset-world
  "Reset streams and time while preserving interactive controls."
  [world]
  (let [reset (initial-world (:config world) (:seed world))]
    (-> reset
        (assoc :speed-multiplier (:speed-multiplier world)
               :theme-index (:theme-index world))
        (assoc-in [:clock :paused?] (get-in world [:clock :paused?]))
        (assoc-in [:clock :simulation-fps]
                  (get-in world [:clock :simulation-fps])))))
