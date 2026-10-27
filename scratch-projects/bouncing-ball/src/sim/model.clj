;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
;;
;; MODEL LEVEL GUIDE
;; -----------------
;; Keep simulation truth here as pure data transformations. Use the comment
;; block at the bottom from any REPL prompt before involving runtime or Quil.
(ns sim.model
  (:require
   [sim.config :as config]
   [sim.random :as random]))

(def default-seed 2026)

(defn dir->vel
  "Convert scalar speed and direction in radians to velocity components."
  [speed direction]
  {:vx (* (double speed) (Math/cos (double direction)))
   :vy (* (double speed) (Math/sin (double direction)))})

(defn- initial-ball [simulation-config]
  (let [{:keys [x y speed direction]}
        (get-in simulation-config [:ball :initial])
        {:keys [vx vy]} (dir->vel speed direction)]
    {:x x
     :y y
     :vx vx
     :vy vy
     :radius (get-in simulation-config [:ball :radius])}))

(defn- clamp [value low high]
  (max low (min high value)))

(defn advance-axis
  "Advance one axis through perfectly elastic walls.

  The loop consumes every collision, so overshoot and long batched steps do
  not leave the ball outside the valid interval. Returns [position velocity]."
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

(defn update-ball
  "Advance a ball by elapsed wall-independent simulation seconds."
  [simulation-config speed-mult elapsed {:keys [x y vx vy radius] :as ball}]
  (let [scaled-elapsed (* (double speed-mult) (double elapsed))
        x-min (double radius)
        y-min (double radius)
        x-max (- (double (config/grid-width simulation-config)) radius)
        y-max (- (double (config/grid-height simulation-config)) radius)
        [x' vx'] (advance-axis x vx scaled-elapsed x-min x-max)
        [y' vy'] (advance-axis y vy scaled-elapsed y-min y-max)]
    (assoc ball :x x' :y y' :vx vx' :vy vy')))

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
      :speed-mult (get-in simulation-config [:ball :speed-multiplier :initial])
      :theme-index 0
      :sim-fps (get-in simulation-config [:clock :simulation-fps :initial])
      :ball (initial-ball simulation-config)})))

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
          (update :ball
                  #(update-ball simulation-config
                                (:speed-mult world)
                                elapsed
                                %))))))

(defn update-world
  "Advance the world by one fixed simulation step."
  ([world]
   (update-world world (/ 1.0 (:sim-fps world))))
  ([world dt]
   (update-world-steps world 1 dt)))

(defn toggle-paused [world]
  (update world :paused? not))

(defn adjust-speed [world direction]
  (let [{:keys [min max]} (get-in world [:config :ball :speed-multiplier])]
    (update world :speed-mult
            #(if (pos? direction)
               (clojure.core/min max (* 2.0 %))
               (clojure.core/max min (/ % 2.0))))))

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
           :speed-mult (:speed-mult world)
           :theme-index (:theme-index world)
           :sim-fps (:sim-fps world))))

(comment
  ;; MODEL GUIDE FOR A REPL PROMPT
  ;; Copy/evaluate this require first. Use :reload after editing model code.
  (require '[sim.config :as config]
           '[sim.model :as model]
           :reload)

  ;; 1. Create and inspect a deterministic starting world.
  (def world-0 (model/initial-world 42))
  (select-keys world-0
               [:seed :tick :elapsed-seconds :paused? :speed-mult :sim-fps])
  (:ball world-0)

  ;; 2. Keep before/after values when testing pure transitions.
  (def world-1 (model/update-world world-0))
  (:tick world-0) ; => 0
  (:tick world-1) ; => 1
  (= world-0 world-1) ; => false

  ;; 3. Fixed-step batches are the boundary used by runtime and driver.
  (def world-60
    (model/update-world-steps world-0 60 (/ 1.0 60.0)))
  (select-keys world-60 [:tick :elapsed-seconds])
  (:ball world-60)

  ;; 4. Assert invariants immediately while experimenting.
  (let [{:keys [x y radius]} (:ball world-60)
        width (config/grid-width (:config world-60))
        height (config/grid-height (:config world-60))]
    (assert (<= radius x (- width radius)))
    (assert (<= radius y (- height radius))))
  (assert (= world-0 (model/initial-world 42)))

  ;; 5. Force a wall collision without changing production globals.
  (def edge-world
    (assoc world-0 :ball {:x 39.4 :y 10.0
                          :vx 12.0 :vy 0.0 :radius 0.45}))
  (def bounced-world (model/update-world edge-world 0.1))
  (:ball bounced-world) ; x stays inside and vx becomes negative

  ;; 6. Controls are also pure world transformations.
  (select-keys (model/adjust-speed world-0 1) [:speed-mult])
  (def paused-world (model/toggle-paused world-0))
  (= paused-world (model/update-world-steps paused-world 10 0.1)) ; => true
  (:tick (model/reset-world world-60)) ; => 0

  ;; 7. Run the real model tests from a development REPL.
  (require '[clojure.test :as test]
           '[sim.model-test]
           :reload)
  (test/run-tests 'sim.model-test)
  )
