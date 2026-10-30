;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.model.core
  "Own the immutable lifecycle of one fireworks show.

  WHY: construction, clock, show membership, controls, and advancement form one
  readable world workflow. DATA: empty-clock/show/world document persistent
  runtime shape. OPERATIONS: construct, advance, reset, and change controls.
  BOUNDARY: firework owns stage transitions; spark owns burst physics;
  configuration, projection, rendering, and runtime mutation stay outside."
  (:require
   [fireworks.model.config :as config]
   [fireworks.model.firework :as firework]
   [fireworks.utility.random :as random]
   [fireworks.utility.theme :as theme]))

(def default-seed 2026)

(def empty-clock
  {:tick 0
   :elapsed-seconds 0.0
   :paused? false
   :simulation-fps 0})

(def empty-show
  {:fireworks []
   :next-id 0})

(def empty-world
  {:config nil
   :seed 0
   :random-state 0
   :theme-index 0
   :clock empty-clock
   :show empty-show})

(defn firework-count [world]
  (count (get-in world [:show :fireworks])))

(defn- start-clock [simulation-config]
  (assoc empty-clock
         :simulation-fps
         (get-in simulation-config [:clock :simulation-fps :initial])))

(defn- construct-show [simulation-config firework-count]
  {:fireworks (firework/make-fireworks simulation-config firework-count)
   :next-id firework-count})

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
         seed (random/u32 seed)
         firework-count (get-in simulation-config [:rockets :count :initial])]
     (assoc empty-world
            :config simulation-config
            :seed seed
            :random-state seed
            :clock (start-clock simulation-config)
            :show (construct-show simulation-config firework-count)))))

(defn- validate-step! [step-count dt]
  (when-not (nat-int? step-count)
    (throw (ex-info "Step count must be a non-negative integer"
                    {:step-count step-count})))
  (when-not (and (number? dt)
                 (Double/isFinite (double dt))
                 (pos? dt))
    (throw (ex-info "Step duration must be positive and finite"
                    {:dt dt}))))

(defn- advance-clock [world dt]
  (-> world
      (update-in [:clock :tick] inc)
      (update-in [:clock :elapsed-seconds] + dt)))

(defn- advance-show [world dt]
  (let [[random-state fireworks]
        (firework/advance-fireworks (:config world)
                                    (:random-state world)
                                    dt
                                    (get-in world [:show :fireworks]))]
    (-> world
        (assoc :random-state random-state)
        (assoc-in [:show :fireworks] fireworks))))

(defn- advance-once [world dt]
  (-> world
      (advance-show dt)
      (advance-clock dt)))

(defn advance-world-steps
  "Advance a running show through a number of fixed-duration steps."
  [world step-count dt]
  (validate-step! step-count dt)
  (if (or (get-in world [:clock :paused?]) (zero? step-count))
    world
    (loop [world world
           steps-left step-count]
      (if (zero? steps-left)
        world
        (recur (advance-once world dt) (dec steps-left))))))

(defn advance-world
  "Advance a running show by one step."
  ([world]
   (advance-world world (/ 1.0 (get-in world [:clock :simulation-fps]))))
  ([world dt]
   (advance-world-steps world 1 dt)))

(defn toggle-paused [world]
  (update-in world [:clock :paused?] not))

(defn change-simulation-speed [world amount]
  (let [{:keys [min max]} (get-in world [:config :clock :simulation-fps])]
    (update-in world [:clock :simulation-fps]
               #(clojure.core/min max
                                  (clojure.core/max min (+ % amount))))))

(defn cycle-theme [world direction]
  (update world :theme-index #(theme/cycle-index % direction)))

(defn- add-firework [world]
  (let [id (get-in world [:show :next-id])
        delay (get-in world [:config :rockets :new-launch-delay-seconds])]
    (-> world
        (update-in [:show :fireworks]
                   conj
                   (firework/wait-before-launch id delay))
        (update-in [:show :next-id] inc))))

(defn- add-fireworks [world amount]
  (loop [world world
         remaining amount]
    (if (zero? remaining)
      world
      (recur (add-firework world) (dec remaining)))))

(defn change-firework-count
  "Add or remove fireworks within the configured limits."
  [world amount]
  (let [{:keys [min max]} (get-in world [:config :rockets :count])
        current (firework-count world)
        target (clojure.core/min max
                                 (clojure.core/max min (+ current amount)))]
    (cond
      (> target current)
      (add-fireworks world (- target current))

      (< target current)
      (update-in world [:show :fireworks] #(vec (take target %)))

      :else world)))

(defn restart-show
  "Restart from the original seed while preserving interactive settings."
  [world]
  (-> world
      (assoc :random-state (:seed world))
      (assoc :clock (assoc (:clock world)
                           :tick 0
                           :elapsed-seconds 0.0
                           :paused? false))
      (assoc :show (construct-show (:config world)
                                   (firework-count world)))))

(defn resize-world
  "Reconstruct the show for a new grid while preserving runtime preferences."
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
        (-> world
            (assoc :config simulation-config)
            (assoc :show (construct-show simulation-config
                                         (firework-count world))))))))
