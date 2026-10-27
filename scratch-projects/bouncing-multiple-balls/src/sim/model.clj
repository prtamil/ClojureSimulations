;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
;;
;; MODEL LEVEL GUIDE
;; -----------------
;; The world is immutable data. Wall and ball collisions are pure functions;
;; runtime atoms and renderers remain outside this namespace.
(ns sim.model
  (:require
   [sim.config :as config]
   [sim.random :as random]))

(def default-seed 2026)
(def ^:private collision-epsilon 1.0e-12)

(defn- random-between [state low high]
  (let [[state' sample] (random/next-double state)]
    [state' (+ (double low) (* sample (- (double high) (double low))))]))

(defn- candidate-ball [simulation-config state id]
  (let [width (double (config/grid-width simulation-config))
        height (double (config/grid-height simulation-config))
        radius-min (get-in simulation-config [:balls :radius :min])
        radius-max (get-in simulation-config [:balls :radius :max])
        speed-min (get-in simulation-config [:balls :speed :min])
        speed-max (get-in simulation-config [:balls :speed :max])
        band-count (get-in simulation-config [:render :band-count])
        [state radius] (random-between state radius-min radius-max)
        [state x] (random-between state radius (- width radius))
        [state y] (random-between state radius (- height radius))
        [state speed] (random-between state speed-min speed-max)
        [state turn] (random/next-double state)
        [state color] (random/next-int state band-count)
        direction (* 2.0 Math/PI turn)]
    [state {:id id
            :x x
            :y y
            :vx (* speed (Math/cos direction))
            :vy (* speed (Math/sin direction))
            :radius radius
            :mass (* radius radius)
            :color color}]))

(defn balls-overlap?
  "True when two circular balls geometrically overlap."
  [ball-a ball-b]
  (let [dx (- (:x ball-b) (:x ball-a))
        dy (- (:y ball-b) (:y ball-a))
        minimum-distance (+ (:radius ball-a) (:radius ball-b))]
    (< (+ (* dx dx) (* dy dy))
       (* minimum-distance minimum-distance))))

(defn- spawn-one [simulation-config state id existing-balls]
  (let [attempt-limit (get-in simulation-config [:balls :spawn-attempts])]
    (loop [state state
           attempt 0]
      (when (= attempt attempt-limit)
        (throw (ex-info "Unable to place all balls without overlap"
                        {:path [:balls :spawn-attempts]
                         :ball-id id
                         :attempts attempt-limit})))
      (let [[state' candidate] (candidate-ball simulation-config state id)]
        (if (some #(balls-overlap? candidate %) existing-balls)
          (recur state' (inc attempt))
          [state' candidate])))))

(defn- spawn-balls [simulation-config state]
  (loop [id 0
         state state
         balls []]
    (if (= id (get-in simulation-config [:balls :count]))
      [state balls]
      (let [[state' ball] (spawn-one simulation-config state id balls)]
        (recur (inc id) state' (conj balls ball))))))

(defn- clamp [value low high]
  (max low (min high value)))

(defn advance-axis
  "Advance one axis through elastic walls, consuming every overshoot."
  [position velocity elapsed low high]
  (let [position (clamp (double position) (double low) (double high))
        velocity (double velocity)
        velocity (cond
                   (and (<= position low) (neg? velocity)) (- velocity)
                   (and (>= position high) (pos? velocity)) (- velocity)
                   :else velocity)]
    (loop [position position
           velocity velocity
           remaining (double elapsed)]
      (if (or (zero? remaining) (zero? velocity))
        [position velocity]
        (let [wall (if (pos? velocity) high low)
              time-to-wall (/ (- wall position) velocity)]
          (if (< remaining time-to-wall)
            [(+ position (* velocity remaining)) velocity]
            (recur (double wall)
                   (- velocity)
                   (max 0.0 (- remaining time-to-wall)))))))))

(defn- move-ball [simulation-config speed-mult elapsed
                  {:keys [x y vx vy radius] :as ball}]
  (let [scaled-elapsed (* speed-mult elapsed)
        x-max (- (double (config/grid-width simulation-config)) radius)
        y-max (- (double (config/grid-height simulation-config)) radius)
        [x' vx'] (advance-axis x vx scaled-elapsed radius x-max)
        [y' vy'] (advance-axis y vy scaled-elapsed radius y-max)]
    (assoc ball :x x' :y y' :vx vx' :vy vy')))

(defn resolve-ball-collision
  "Resolve overlap and an approaching impulse for two circular bodies."
  [restitution ball-a ball-b]
  (let [dx (- (:x ball-b) (:x ball-a))
        dy (- (:y ball-b) (:y ball-a))
        distance-squared (+ (* dx dx) (* dy dy))
        minimum-distance (+ (:radius ball-a) (:radius ball-b))]
    (if (> distance-squared (* minimum-distance minimum-distance))
      [ball-a ball-b]
      (let [distance (Math/sqrt distance-squared)
            [nx ny] (if (> distance collision-epsilon)
                      [(/ dx distance) (/ dy distance)]
                      (if (< (:id ball-a) (:id ball-b)) [1.0 0.0] [-1.0 0.0]))
            inverse-mass-a (/ 1.0 (:mass ball-a))
            inverse-mass-b (/ 1.0 (:mass ball-b))
            inverse-mass-sum (+ inverse-mass-a inverse-mass-b)
            penetration (- minimum-distance distance)
            correction-a (/ (* penetration inverse-mass-a) inverse-mass-sum)
            correction-b (/ (* penetration inverse-mass-b) inverse-mass-sum)
            ball-a (-> ball-a
                       (update :x - (* nx correction-a))
                       (update :y - (* ny correction-a)))
            ball-b (-> ball-b
                       (update :x + (* nx correction-b))
                       (update :y + (* ny correction-b)))
            relative-vx (- (:vx ball-b) (:vx ball-a))
            relative-vy (- (:vy ball-b) (:vy ball-a))
            velocity-along-normal (+ (* relative-vx nx) (* relative-vy ny))]
        (if (neg? velocity-along-normal)
          (let [impulse (/ (* (- (+ 1.0 restitution))
                              velocity-along-normal)
                           inverse-mass-sum)
                impulse-x (* impulse nx)
                impulse-y (* impulse ny)]
            [(-> ball-a
                 (update :vx - (* impulse-x inverse-mass-a))
                 (update :vy - (* impulse-y inverse-mass-a)))
             (-> ball-b
                 (update :vx + (* impulse-x inverse-mass-b))
                 (update :vy + (* impulse-y inverse-mass-b)))])
          [ball-a ball-b])))))

(defn- collision-pairs [ball-count]
  (for [left (range ball-count)
        right (range (inc left) ball-count)]
    [left right]))

(defn- resolve-collisions [simulation-config balls]
  (let [restitution (get-in simulation-config [:physics :restitution])]
    (reduce (fn [current-balls [left right]]
              (let [[ball-a ball-b]
                    (resolve-ball-collision restitution
                                            (nth current-balls left)
                                            (nth current-balls right))]
                (assoc current-balls left ball-a right ball-b)))
            balls
            (collision-pairs (count balls)))))

(defn- confine-ball [simulation-config
                     {:keys [x y vx vy radius] :as ball}]
  (let [x-max (- (double (config/grid-width simulation-config)) radius)
        y-max (- (double (config/grid-height simulation-config)) radius)
        [x' vx'] (advance-axis x vx 0.0 radius x-max)
        [y' vy'] (advance-axis y vy 0.0 radius y-max)]
    (assoc ball :x x' :y y' :vx vx' :vy vy')))

(defn- simulate-substep [world balls substep-dt]
  (let [simulation-config (:config world)
        moved (mapv #(move-ball simulation-config
                                (:speed-mult world)
                                substep-dt
                                %)
                    balls)
        iterations (get-in simulation-config [:physics :collision-iterations])]
    (loop [remaining iterations
           balls moved]
      (if (zero? remaining)
        balls
        (recur (dec remaining)
               (mapv (partial confine-ball simulation-config)
                     (resolve-collisions simulation-config balls)))))))

(defn- simulate-tick [world balls dt]
  (let [substeps (get-in world [:config :physics :substeps])
        substep-dt (/ (double dt) substeps)]
    (loop [remaining substeps
           balls balls]
      (if (zero? remaining)
        balls
        (recur (dec remaining)
               (simulate-substep world balls substep-dt))))))

(defn initial-world
  ([] (initial-world config/default-config default-seed))
  ([seed-or-config]
   (if (map? seed-or-config)
     (initial-world seed-or-config default-seed)
     (initial-world config/default-config seed-or-config)))
  ([simulation-config seed]
   (let [simulation-config (config/validate! simulation-config)
         [rng-state permutation] (random/shuffled-permutation seed)
         [rng-state balls] (spawn-balls simulation-config rng-state)]
     {:config simulation-config
      :seed seed
      :rng-state rng-state
      :noise-permutation permutation
      :tick 0
      :elapsed-seconds 0.0
      :paused? false
      :speed-mult (get-in simulation-config [:balls :speed-multiplier :initial])
      :theme-index 0
      :sim-fps (get-in simulation-config [:clock :simulation-fps :initial])
      :balls balls})))

(defn update-world-steps [world tick-count dt]
  (when-not (nat-int? tick-count)
    (throw (ex-info "Tick count must be a non-negative integer"
                    {:tick-count tick-count})))
  (when-not (and (number? dt) (Double/isFinite (double dt)) (pos? dt))
    (throw (ex-info "Fixed-step duration must be a positive finite number"
                    {:dt dt})))
  (if (or (zero? tick-count) (:paused? world))
    world
    (loop [remaining tick-count
           balls (:balls world)]
      (if (zero? remaining)
        (assoc world
               :tick (+ (:tick world) tick-count)
               :elapsed-seconds (+ (:elapsed-seconds world)
                                   (* tick-count (double dt)))
               :balls balls)
        (recur (dec remaining) (simulate-tick world balls dt))))))

(defn update-world
  ([world] (update-world world (/ 1.0 (:sim-fps world))))
  ([world dt] (update-world-steps world 1 dt)))

(defn toggle-paused [world]
  (update world :paused? not))

(defn adjust-speed [world direction]
  (let [{:keys [min max]} (get-in world [:config :balls :speed-multiplier])]
    (update world :speed-mult
            #(if (pos? direction)
               (clojure.core/min max (* 2.0 %))
               (clojure.core/max min (/ % 2.0))))))

(defn adjust-sim-fps [world delta]
  (let [{:keys [min max]} (get-in world [:config :clock :simulation-fps])]
    (update world :sim-fps
            #(clojure.core/min max (clojure.core/max min (+ % delta))))))

(defn reset-world [world]
  (let [fresh (initial-world (:config world) (:seed world))]
    (assoc fresh
           :paused? (:paused? world)
           :speed-mult (:speed-mult world)
           :theme-index (:theme-index world)
           :sim-fps (:sim-fps world))))

(comment
  ;; MODEL GUIDE FOR A REPL PROMPT
  (require '[sim.config :as config]
           '[sim.model :as model]
           :reload)

  (def world-0 (model/initial-world 42))
  (select-keys world-0
               [:seed :tick :elapsed-seconds :paused? :speed-mult :sim-fps])
  (count (:balls world-0))
  (take 3 (:balls world-0))

  (def world-60
    (model/update-world-steps world-0 60 (/ 1.0 60.0)))
  (select-keys world-60 [:tick :elapsed-seconds])

  ;; Check bounds and non-overlapping deterministic spawning.
  (every? (fn [{:keys [x y radius]}]
            (and (<= radius x (- (config/grid-width (:config world-0)) radius))
                 (<= radius y (- (config/grid-height (:config world-0)) radius))))
          (:balls world-60))
  (for [left (range (count (:balls world-0)))
        right (range (inc left) (count (:balls world-0)))]
    (model/balls-overlap? (nth (:balls world-0) left)
                          (nth (:balls world-0) right)))

  ;; Directly inspect an equal-mass head-on collision.
  (model/resolve-ball-collision
   1.0
   {:id 0 :x 10.0 :y 10.0 :vx 1.0 :vy 0.0
    :radius 0.5 :mass 0.25 :color 0}
   {:id 1 :x 11.0 :y 10.0 :vx -1.0 :vy 0.0
    :radius 0.5 :mass 0.25 :color 1})

  ;; Run model tests from a development REPL.
  (require '[clojure.test :as test]
           '[sim.model-test]
           :reload)
  (test/run-tests 'sim.model-test)
  )
