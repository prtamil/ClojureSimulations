;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.ball
  "Seeded ball creation and radius-aware wall motion."
  (:require
   [sim.model.config :as config]
   [sim.utility.random :as random]))

(defn- between [state low high]
  (let [[state sample] (random/next-double state)]
    [state (+ (double low) (* sample (- (double high) (double low))))]))

(defn- candidate [simulation-config state id]
  (let [width (double (config/grid-width simulation-config))
        height (double (config/grid-height simulation-config))
        {radius-min :min radius-max :max} (get-in simulation-config
                                                   [:balls :radius])
        {speed-min :min speed-max :max} (get-in simulation-config
                                                 [:balls :speed])
        band-count (get-in simulation-config [:render :band-count])
        [state radius] (between state radius-min radius-max)
        [state x] (between state radius (- width radius))
        [state y] (between state radius (- height radius))
        [state speed] (between state speed-min speed-max)
        [state turn] (random/next-double state)
        [state band] (random/next-int state band-count)
        angle (* 2.0 Math/PI turn)]
    [state {:id id
            :x x :y y
            :vx (* speed (Math/cos angle))
            :vy (* speed (Math/sin angle))
            :radius radius
            :mass (* radius radius)
            :color band}]))

(defn overlap? [left right]
  (let [dx (- (:x right) (:x left))
        dy (- (:y right) (:y left))
        distance (+ (:radius left) (:radius right))]
    (< (+ (* dx dx) (* dy dy)) (* distance distance))))

(defn- spawn-one [simulation-config state id existing]
  (let [attempt-limit (get-in simulation-config [:balls :spawn-attempts])]
    (loop [state state
           remaining attempt-limit]
      (when (zero? remaining)
        (throw (ex-info "Unable to place all balls without overlap"
                        {:path [:balls :spawn-attempts]
                         :ball-id id
                         :attempts attempt-limit})))
      (let [[state candidate] (candidate simulation-config state id)]
        (if (some #(overlap? candidate %) existing)
          (recur state (dec remaining))
          [state candidate])))))

(defn spawn-many [simulation-config state]
  (reduce (fn [[state balls] id]
            (let [[state ball] (spawn-one simulation-config state id balls)]
              [state (conj balls ball)]))
          [state []]
          (range (get-in simulation-config [:balls :count]))))

(defn- clamp [value low high]
  (max low (min high value)))

(defn advance-axis [position velocity seconds low high]
  (let [position (clamp (double position) (double low) (double high))
        velocity (cond
                   (and (<= position low) (neg? velocity)) (- velocity)
                   (and (>= position high) (pos? velocity)) (- velocity)
                   :else (double velocity))]
    (loop [position position velocity velocity remaining (double seconds)]
      (if (or (zero? remaining) (zero? velocity))
        [position velocity]
        (let [wall (if (pos? velocity) high low)
              until-wall (/ (- wall position) velocity)]
          (if (< remaining until-wall)
            [(+ position (* velocity remaining)) velocity]
            (recur (double wall) (- velocity)
                   (max 0.0 (- remaining until-wall)))))))))

(defn advance [simulation-config speed-multiplier seconds
               {:keys [x y vx vy radius] :as ball}]
  (let [seconds (* speed-multiplier seconds)
        x-max (- (double (config/grid-width simulation-config)) radius)
        y-max (- (double (config/grid-height simulation-config)) radius)
        [x vx] (advance-axis x vx seconds radius x-max)
        [y vy] (advance-axis y vy seconds radius y-max)]
    (assoc ball :x x :y y :vx vx :vy vy)))

(defn confine [simulation-config {:keys [x y vx vy radius] :as ball}]
  (let [x-max (- (double (config/grid-width simulation-config)) radius)
        y-max (- (double (config/grid-height simulation-config)) radius)
        [x vx] (advance-axis x vx 0.0 radius x-max)
        [y vy] (advance-axis y vy 0.0 radius y-max)]
    (assoc ball :x x :y y :vx vx :vy vy)))
