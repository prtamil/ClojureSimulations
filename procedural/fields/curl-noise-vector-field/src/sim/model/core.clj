;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.core
  "Immutable orchestration for a curl field, particles, and semantic cells."
  (:require
   [sim.model.config :as config]
   [sim.model.field :as field]
   [sim.model.particle :as particle]
   [sim.model.trail :as trail]
   [sim.utility.random :as random]))

(defn- static-cells [world pattern]
  (if (config/static-pattern? pattern)
    (field/sample-grid (:config world) (:field world) pattern)
    {}))

(defn- select-pattern [world pattern]
  (assoc world
         :pattern pattern
         :cells (static-cells world pattern)))

(defn reset-world
  "Reseed the field and particles while preserving interactive controls."
  [world]
  (let [simulation-config (:config world)
        [rng-state permutation]
        (random/shuffled-permutation (:rng-state world))
        [rng-state particles]
        (particle/spawn-many simulation-config rng-state)
        world (assoc world
                     :tick 0
                     :field (field/create permutation)
                     :flash-intensity (get-in simulation-config
                                              [:flash :initial])
                     :particles particles
                     :cells {}
                     :rng-state rng-state)]
    (select-pattern world (:pattern world))))

(defn initial-world
  ([] (initial-world config/default-config (System/nanoTime)))
  ([seed]
   (initial-world config/default-config seed))
  ([simulation-config seed]
   (let [simulation-config (config/validate! simulation-config)]
     (reset-world
      {:config simulation-config
       :tick 0
       :field (field/create [])
       :flash-intensity (get-in simulation-config [:flash :initial])
       :paused? false
       :speed (get-in simulation-config [:particles :speed :initial])
       :drift-mult (get-in simulation-config
                           [:field :drift-multiplier :initial])
       :theme-index 0
       :pattern :particles
       :sim-fps (get-in simulation-config [:clock :simulation-fps :initial])
       :particles []
       :cells {}
       :rng-state (long seed)}))))

(defn toggle-paused [world]
  (update world :paused? not))

(defn- cycle-index [items current direction]
  (mod (+ (.indexOf ^java.util.List items current) direction) (count items)))

(defn cycle-pattern [world direction]
  (let [patterns (get-in world [:config :views :order])
        pattern (nth patterns (cycle-index patterns (:pattern world) direction))]
    (select-pattern world pattern)))

(defn adjust-speed [world direction]
  (let [simulation-config (:config world)
        speed-min (get-in simulation-config [:particles :speed :min])
        speed-max (get-in simulation-config [:particles :speed :max])
        drift-min (get-in simulation-config [:field :drift-multiplier :min])
        drift-max (get-in simulation-config [:field :drift-multiplier :max])
        adjust (if (pos? direction)
                 #(min %2 (* 2 %1))
                 #(max %2 (quot %1 2)))]
    (-> world
        (update :speed adjust (if (pos? direction) speed-max speed-min))
        (update :drift-mult adjust
                (if (pos? direction) drift-max drift-min)))))

(defn adjust-sim-fps [world delta]
  (let [{:keys [min max]} (get-in world
                                  [:config :clock :simulation-fps])]
    (update world :sim-fps #(-> (+ % delta) (clojure.core/max min)
                                (clojure.core/min max)))))

(defn- fade-flash [world seconds]
  (let [decay (get-in world [:config :flash :decay])]
    (update world :flash-intensity #(* % (Math/exp (* -1.0 decay seconds))))))

(defn- advance-field [world seconds]
  (update world :field #(field/advance (:config world) %
                                      (:drift-mult world) seconds)))

(defn- particle-step [world seconds]
  (let [world (-> world
                  (update :tick inc)
                  (advance-field seconds)
                  (fade-flash seconds))
        simulation-config (:config world)
        warped? (= :warped (:pattern world))
        {:keys [rng-state particles deposits]}
        (particle/advance-all simulation-config
                              (:field world)
                              (:speed world)
                              warped?
                              seconds
                              (:rng-state world)
                              (:particles world))
        cells (trail/deposit simulation-config
                             (trail/fade simulation-config
                                         (:cells world)
                                         seconds)
                             deposits)]
    (assoc world
           :rng-state rng-state
           :particles particles
           :cells cells)))

(defn- static-steps [world tick-count seconds]
  (let [elapsed (* tick-count seconds)
        world (-> world
                  (update :tick + tick-count)
                  (advance-field elapsed)
                  (fade-flash elapsed))]
    (assoc world :cells (static-cells world (:pattern world)))))

(defn- validate-step! [world tick-count seconds]
  (when-not (nat-int? tick-count)
    (throw (ex-info "Tick count must be a non-negative integer"
                    {:tick-count tick-count})))
  (when-not (and (number? seconds)
                 (Double/isFinite (double seconds))
                 (pos? seconds))
    (throw (ex-info "Fixed-step duration must be a positive finite number"
                    {:dt seconds})))
  (when-not (config/supported-pattern? (:pattern world))
    (throw (ex-info "World contains an unsupported pattern"
                    {:pattern (:pattern world)}))))

(defn update-world-steps
  "Advance a fixed-step batch. Particles integrate every step; static views
  advance the same elapsed time and sample their final field once."
  [world tick-count seconds]
  (validate-step! world tick-count seconds)
  (cond
    (or (zero? tick-count) (:paused? world)) world
    (config/particle-pattern? (:pattern world))
    (nth (iterate #(particle-step % seconds) world) tick-count)
    :else
    (static-steps world tick-count seconds)))

(defn update-world
  "Advance the world by one fixed simulation step."
  ([world]
   (update-world world (/ 1.0 (:sim-fps world))))
  ([world seconds]
   (update-world-steps world 1 seconds)))

(defn field-time [world]
  (get-in world [:field :time]))

(defn field-permutation [world]
  (get-in world [:field :permutation]))
