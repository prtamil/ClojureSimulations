;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.ball
  "Pure construction and radius-aware motion for one elastic ball."
  (:require [sim.model.config :as config]))

(defn direction->velocity [speed direction]
  {:vx (* (double speed) (Math/cos (double direction)))
   :vy (* (double speed) (Math/sin (double direction)))})

(defn create [simulation-config]
  (let [{:keys [x y speed direction]} (get-in simulation-config
                                               [:ball :initial])]
    (merge {:x x :y y :radius (get-in simulation-config [:ball :radius])}
           (direction->velocity speed direction))))

(defn- clamp [value low high]
  (max low (min high value)))

(defn advance-axis
  "Advance one coordinate through any number of perfectly elastic wall hits."
  [position velocity seconds low high]
  (let [position (clamp (double position) (double low) (double high))
        velocity (cond
                   (and (<= position low) (neg? velocity)) (- velocity)
                   (and (>= position high) (pos? velocity)) (- velocity)
                   :else (double velocity))]
    (loop [position position
           velocity velocity
           remaining (double seconds)]
      (if (or (zero? remaining) (zero? velocity))
        [position velocity]
        (let [wall (if (pos? velocity) high low)
              time-to-wall (/ (- wall position) velocity)]
          (if (< remaining time-to-wall)
            [(+ position (* velocity remaining)) velocity]
            (recur (double wall)
                   (- velocity)
                   (max 0.0 (- remaining time-to-wall)))))))))

(defn advance
  [simulation-config speed-multiplier seconds
   {:keys [x y vx vy radius] :as ball}]
  (let [seconds (* speed-multiplier seconds)
        x-max (- (double (config/grid-width simulation-config)) radius)
        y-max (- (double (config/grid-height simulation-config)) radius)
        [x vx] (advance-axis x vx seconds radius x-max)
        [y vy] (advance-axis y vy seconds radius y-max)]
    (assoc ball :x x :y y :vx vx :vy vy)))
