;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.core
  "Own the immutable lifecycle of the bouncing-ball world.

  WHY: construction, time, controls, reset, and render projection form one
  world lifecycle and should read in one place.

  DATA: empty-world documents the complete persistent runtime shape.
  OPERATIONS: construct, advance, project, pause, reset, and change controls.
  BOUNDARY: sim.model.ball owns elastic-collision mathematics;
  sim.model.config owns settings and validation; renderers own presentation."
  (:require
   [sim.model.ball :as ball]
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
   :speed-multiplier 1.0
   :theme-index 0
   :ball nil})

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
            :speed-multiplier
            (get-in simulation-config [:ball :speed-multiplier :initial])
            :ball (ball/create simulation-config)))))

(defn- validate-duration! [label seconds non-negative?]
  (when-not (and (number? seconds)
                 (Double/isFinite (double seconds))
                 (if non-negative? (not (neg? seconds)) (pos? seconds)))
    (throw (ex-info (str label " must be "
                         (if non-negative? "non-negative" "positive")
                         " and finite")
                    {:seconds seconds}))))

(defn- validate-step! [step-count seconds]
  (when-not (nat-int? step-count)
    (throw (ex-info "Step count must be a non-negative integer"
                    {:step-count step-count})))
  (validate-duration! "Fixed-step duration" seconds false))

(defn advance-world-steps
  "Advance a batch while preserving exact elastic-wall behavior."
  [world step-count seconds]
  (validate-step! step-count seconds)
  (if (or (zero? step-count) (get-in world [:clock :paused?]))
    world
    (let [elapsed-seconds (* step-count (double seconds))]
      (-> world
          (update-in [:clock :tick] + step-count)
          (update-in [:clock :elapsed-seconds] + elapsed-seconds)
          (update :ball #(ball/advance (:config world)
                                      (:speed-multiplier world)
                                      elapsed-seconds
                                      %))))))

(defn advance-world
  "Advance the world by one fixed simulation step."
  ([world]
   (advance-world world
                  (/ 1.0 (get-in world [:clock :simulation-fps]))))
  ([world seconds]
   (advance-world-steps world 1 seconds)))

(defn project-world
  "Project motion for a renderer without advancing simulation truth."
  [world seconds]
  (validate-duration! "Projection duration" seconds true)
  (if (or (zero? seconds) (get-in world [:clock :paused?]))
    world
    (update world :ball #(ball/advance (:config world)
                                      (:speed-multiplier world)
                                      seconds
                                      %))))

(defn toggle-paused [world]
  (update-in world [:clock :paused?] not))

(defn change-speed [world direction]
  (let [{:keys [min max]} (get-in world [:config :ball :speed-multiplier])]
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
  "Reset motion and time while preserving interactive controls."
  [world]
  (let [reset (initial-world (:config world) (:seed world))]
    (-> reset
        (assoc :speed-multiplier (:speed-multiplier world)
               :theme-index (:theme-index world))
        (assoc-in [:clock :paused?] (get-in world [:clock :paused?]))
        (assoc-in [:clock :simulation-fps]
                  (get-in world [:clock :simulation-fps])))))
