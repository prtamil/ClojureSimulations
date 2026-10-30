;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.core
  "Own the complete immutable simulation and its lifecycle.

  WHY: field time, particles, and their fading trail advance as one world.
  Keeping that story together makes a simulation step readable in one place.

  DATA: the world shape is documented by empty-world below.
  OPERATIONS: construct, reset, advance, switch views, and change controls.
  BOUNDARY: field mathematics remains in sim.model.field; drawing remains in
  sim.render namespaces. Particles and trails are ordinary world data, not
  independent subsystems."
  (:require
   [sim.model.config :as config]
   [sim.model.field :as field]
   [sim.utility.random :as random]))

(def empty-clock
  {:tick 0
   :paused? false
   :simulation-fps 0})

(def empty-view
  {:pattern :particles
   :samples {}})

(def empty-world
  {:config nil
   :random-state 0
   :clock empty-clock
   :field {:time 0.0 :permutation []}
   :particles []
   :trail {}
   :view empty-view
   :particle-speed 0
   :drift-multiplier 0
   :theme-index 0
   :flash-intensity 0.0})

;; Particle   {:position [x y] :band 2
;;             :age-seconds 0.4 :lifetime-seconds 3.7}
;; Trail      {[x y] {:position [x y] :intensity 0.8 :band 2}}
;; Trail hit  {:position [x y] :band 2}

;; Particle creation and movement

(defn- make-particle [position band lifetime-seconds]
  {:position position
   :band band
   :age-seconds 0.0
   :lifetime-seconds lifetime-seconds})

(defn- spawn-particle [simulation-config random-state]
  (let [width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)
        band-count (get-in simulation-config [:render :band-count])
        {:keys [min max]} (get-in simulation-config
                                   [:particles :lifetime-seconds])
        [random-state x] (random/between random-state 0.0 (double width))
        [random-state y] (random/between random-state 0.0 (double height))
        [random-state band] (random/next-int random-state band-count)
        [random-state lifetime] (random/between random-state min max)]
    [random-state (make-particle [x y] band lifetime)]))

(defn- spawn-particles [simulation-config random-state]
  (loop [remaining (get-in simulation-config [:particles :count])
         random-state random-state
         particles []]
    (if (zero? remaining)
      [random-state particles]
      (let [[random-state particle]
            (spawn-particle simulation-config random-state)]
        (recur (dec remaining) random-state (conj particles particle))))))

(defn- in-bounds? [simulation-config [x y]]
  (and (<= 0.0 x) (< x (config/grid-width simulation-config))
       (<= 0.0 y) (< y (config/grid-height simulation-config))))

(defn- move-particle
  [simulation-config field-value speed warped? seconds particle]
  (let [[x y] (:position particle)
        [dx dy] (field/direction simulation-config field-value
                                 (:position particle) warped?)]
    (-> particle
        (assoc :position [(+ x (* dx speed seconds))
                          (+ y (* dy speed seconds))])
        (update :age-seconds + seconds))))

(defn- expired-particle? [simulation-config particle]
  (or (>= (:age-seconds particle) (:lifetime-seconds particle))
      (not (in-bounds? simulation-config (:position particle)))))

(defn- move-particles
  [simulation-config field-value speed warped? seconds random-state particles]
  (reduce
   (fn [{:keys [random-state] :as result} particle]
     (let [moved (move-particle simulation-config field-value speed warped?
                                seconds particle)
           visible? (in-bounds? simulation-config (:position moved))
           result (if visible?
                    (update result :trail-hits conj
                            {:position (mapv int (:position moved))
                             :band (:band moved)})
                    result)
           [random-state next-particle]
           (if (expired-particle? simulation-config moved)
             (spawn-particle simulation-config random-state)
             [random-state moved])]
       (-> result
           (assoc :random-state random-state)
           (update :particles conj next-particle))))
   {:random-state random-state :particles [] :trail-hits []}
   particles))

;; Trail persistence

(defn- fade-trail [trail simulation-config seconds]
  (let [factor (Math/exp (* -1.0
                            (get-in simulation-config [:trail :decay])
                            seconds))
        threshold (get-in simulation-config [:render :glow-threshold])]
    (into {}
          (keep (fn [[position point]]
                  (let [intensity (* factor (:intensity point))]
                    (when (> intensity threshold)
                      [position (assoc point :intensity intensity)]))))
          trail)))

(defn- deposit-particles [trail simulation-config trail-hits]
  (let [intensity (get-in simulation-config [:trail :hit-intensity])]
    (reduce (fn [trail {:keys [position band]}]
              (assoc trail position
                     {:position position
                      :intensity intensity
                      :band band}))
            trail
            trail-hits)))

;; World construction and controls

(defn- start-clock [simulation-config]
  (assoc empty-clock
         :simulation-fps
         (get-in simulation-config [:clock :simulation-fps :initial])))

(defn- current-pattern [world]
  (get-in world [:view :pattern]))

(defn- calculate-view-samples [world]
  (let [pattern (current-pattern world)
        samples (if (config/static-pattern? pattern)
                  (field/sample-grid (:config world) (:field world) pattern)
                  {})]
    (assoc-in world [:view :samples] samples)))

(defn reset-world
  "Reseed the field and particles while preserving interactive controls."
  [world]
  (let [simulation-config (:config world)
        [random-state permutation]
        (random/shuffled-permutation (:random-state world))
        [random-state particles]
        (spawn-particles simulation-config random-state)]
    (-> world
        (assoc :random-state random-state
               :field (field/create permutation)
               :particles particles
               :trail {}
               :flash-intensity (get-in simulation-config [:flash :initial]))
        (assoc-in [:clock :tick] 0)
        calculate-view-samples)))

(defn initial-world
  "Create a world from validated configuration and an explicit random seed."
  ([] (initial-world config/default-config (System/nanoTime)))
  ([seed]
   (initial-world config/default-config seed))
  ([simulation-config seed]
   (let [simulation-config (config/validate! simulation-config)]
     (-> empty-world
         (assoc :config simulation-config
                :random-state (long seed)
                :clock (start-clock simulation-config)
                :particle-speed (get-in simulation-config
                                        [:particles :speed :initial])
                :drift-multiplier (get-in simulation-config
                                          [:field :drift-multiplier :initial]))
         reset-world))))

(defn toggle-paused [world]
  (update-in world [:clock :paused?] not))

(defn- cycle-index [items current direction]
  (mod (+ (.indexOf ^java.util.List items current) direction) (count items)))

(defn cycle-view [world direction]
  (let [patterns (get-in world [:config :views :order])
        pattern (nth patterns
                     (cycle-index patterns (current-pattern world) direction))]
    (-> world
        (assoc :trail {})
        (assoc-in [:view :pattern] pattern)
        calculate-view-samples)))

(defn- change-power-of-two [value direction minimum maximum]
  (if (pos? direction)
    (min maximum (* 2 value))
    (max minimum (quot value 2))))

(defn change-flow-speed [world direction]
  (let [simulation-config (:config world)
        speed-range (get-in simulation-config [:particles :speed])
        drift-range (get-in simulation-config [:field :drift-multiplier])]
    (-> world
        (update :particle-speed change-power-of-two direction
                (:min speed-range) (:max speed-range))
        (update :drift-multiplier change-power-of-two direction
                (:min drift-range) (:max drift-range)))))

(defn change-simulation-fps [world amount]
  (let [{:keys [min max]} (get-in world [:config :clock :simulation-fps])]
    (update-in world [:clock :simulation-fps]
               #(-> (+ % amount)
                    (clojure.core/max min)
                    (clojure.core/min max)))))

;; World advancement

(defn- advance-clock [world tick-count]
  (update-in world [:clock :tick] + tick-count))

(defn- fade-flash [world seconds]
  (let [decay (get-in world [:config :flash :decay])]
    (update world :flash-intensity #(* % (Math/exp (* -1.0 decay seconds))))))

(defn- advance-field [world seconds]
  (update world :field #(field/advance (:config world) %
                                      (:drift-multiplier world) seconds)))

(defn- advance-particle-step [world seconds]
  (let [world (-> world
                  (advance-clock 1)
                  (advance-field seconds)
                  (fade-flash seconds))
        simulation-config (:config world)
        warped? (= :warped (current-pattern world))
        {:keys [random-state particles trail-hits]}
        (move-particles simulation-config
                        (:field world)
                        (:particle-speed world)
                        warped?
                        seconds
                        (:random-state world)
                        (:particles world))
        updated-trail (-> (:trail world)
                          (fade-trail simulation-config seconds)
                          (deposit-particles simulation-config trail-hits))]
    (assoc world
           :random-state random-state
           :particles particles
           :trail updated-trail)))

(defn- advance-particle-steps [world tick-count seconds]
  (loop [world world
         remaining tick-count]
    (if (zero? remaining)
      world
      (recur (advance-particle-step world seconds) (dec remaining)))))

(defn- advance-static-view [world tick-count seconds]
  (let [elapsed-seconds (* tick-count seconds)]
    (-> world
        (advance-clock tick-count)
        (advance-field elapsed-seconds)
        (fade-flash elapsed-seconds)
        calculate-view-samples)))

(defn- validate-step! [world tick-count seconds]
  (when-not (nat-int? tick-count)
    (throw (ex-info "Tick count must be a non-negative integer"
                    {:tick-count tick-count})))
  (when-not (and (number? seconds)
                 (Double/isFinite (double seconds))
                 (pos? seconds))
    (throw (ex-info "Fixed-step duration must be a positive finite number"
                    {:dt seconds})))
  (when-not (config/supported-pattern? (current-pattern world))
    (throw (ex-info "World contains an unsupported pattern"
                    {:pattern (current-pattern world)}))))

(defn advance-world-steps
  "Advance a fixed-step batch through the selected field view."
  [world tick-count seconds]
  (validate-step! world tick-count seconds)
  (cond
    (or (zero? tick-count) (get-in world [:clock :paused?])) world
    (config/particle-pattern? (current-pattern world))
    (advance-particle-steps world tick-count seconds)
    :else
    (advance-static-view world tick-count seconds)))

(defn advance-world
  "Advance the world by one fixed simulation step."
  ([world]
   (advance-world world (/ 1.0 (get-in world [:clock :simulation-fps]))))
  ([world seconds]
   (advance-world-steps world 1 seconds)))

(defn field-time [world]
  (get-in world [:field :time]))

(defn field-permutation [world]
  (get-in world [:field :permutation]))
