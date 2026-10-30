;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.model.spark
  "Own seeded burst creation and short-lived spark physics.

  WHY: radial sampling, gravity, fading, and lifetime filtering are substantial
  rules shared by every bursting firework. DATA: the spark shape appears below.
  OPERATIONS: create a burst, move one spark, and retain living sparks.
  BOUNDARY: firework stages, world time, controls, and rendering remain out."
  (:require
   [fireworks.utility.random :as random]))

(def ^:private full-turn (* 2.0 Math/PI))

;; spark {:position [x y]
;;        :velocity [vx vy]
;;        :life 1.0
;;        :fade-rate 1.2
;;        :glyph \*
;;        :color-index 0}

(defn alive? [spark]
  (pos? (:life spark)))

(defn- make-spark
  [{:keys [simulation-config origin spark-count]} random-state index]
  (let [jitter-max (get-in simulation-config [:sparks :direction-jitter])
        speed-min (get-in simulation-config [:sparks :speed :min])
        speed-max (get-in simulation-config [:sparks :speed :max])
        vertical-scale (get-in simulation-config
                               [:sparks :vertical-speed-scale])
        life-min (get-in simulation-config [:sparks :life :min])
        life-max (get-in simulation-config [:sparks :life :max])
        fade-min (get-in simulation-config [:sparks :fade-rate :min])
        fade-max (get-in simulation-config [:sparks :fade-rate :max])
        symbols (get-in simulation-config [:sparks :symbols])
        [random-state jitter] (if (zero? jitter-max)
                                [random-state 0.0]
                                (random/between random-state 0.0 jitter-max))
        angle (+ (* (/ (double index) spark-count) full-turn) jitter)
        [random-state speed] (random/between random-state speed-min speed-max)
        [random-state life] (random/between random-state life-min life-max)
        [random-state fade-rate] (random/between random-state fade-min fade-max)
        [random-state symbol-index] (random/index random-state (count symbols))
        [random-state color-index] (random/index random-state 7)]
    [random-state
     {:position origin
      :velocity [(* (Math/cos angle) speed)
                 (* (Math/sin angle) speed vertical-scale)]
      :life life
      :fade-rate fade-rate
      :glyph (nth symbols symbol-index)
      :color-index color-index}]))

(defn make-burst
  "Return [random-state sparks] for a seeded radial burst at origin."
  [simulation-config random-state origin]
  (let [burst {:simulation-config simulation-config
               :origin origin
               :spark-count (get-in simulation-config [:sparks :per-burst])}]
    (loop [index 0
           random-state random-state
           sparks []]
      (if (= index (:spark-count burst))
        [random-state sparks]
        (let [[random-state spark] (make-spark burst random-state index)]
          (recur (inc index) random-state (conj sparks spark)))))))

(defn move-spark
  "Move and fade one spark by dt seconds."
  [simulation-config dt {:keys [position velocity life fade-rate] :as spark}]
  (let [[x y] position
        [vx vy] velocity
        gravity (get-in simulation-config [:sparks :gravity])]
    (assoc spark
           :position [(+ x (* vx dt))
                      (+ y (* vy dt))]
           :velocity [vx (+ vy (* gravity dt))]
           :life (- life (* fade-rate dt)))))

(defn move-sparks
  "Move a burst and discard sparks whose lifetime is exhausted."
  [simulation-config dt sparks]
  (->> sparks
       (map #(move-spark simulation-config dt %))
       (filter alive?)
       vec))
