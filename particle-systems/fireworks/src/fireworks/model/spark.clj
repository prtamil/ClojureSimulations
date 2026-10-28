;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.model.spark
  "Creation and time-based motion for the short-lived points in a burst."
  (:require
   [fireworks.utility.random :as random]))

(def ^:private full-turn (* 2.0 Math/PI))

(defn alive? [spark]
  (pos? (:life spark)))

(defn- new-spark
  [simulation-config rng origin index spark-count]
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
        [rng jitter] (if (zero? jitter-max)
                       [rng 0.0]
                       (random/between rng 0.0 jitter-max))
        angle (+ (* (/ (double index) spark-count) full-turn) jitter)
        [rng speed] (random/between rng speed-min speed-max)
        [rng life] (random/between rng life-min life-max)
        [rng fade-rate] (random/between rng fade-min fade-max)
        [rng symbol-index] (random/index rng (count symbols))
        [rng color-index] (random/index rng 7)]
    [rng {:position origin
          :velocity [(* (Math/cos angle) speed)
                     (* (Math/sin angle) speed vertical-scale)]
          :life life
          :fade-rate fade-rate
          :glyph (nth symbols symbol-index)
          :color-index color-index}]))

(defn burst
  "Return [next-rng sparks] for a seeded radial burst at origin."
  [simulation-config rng origin]
  (let [spark-count (get-in simulation-config [:sparks :per-burst])]
    (loop [index 0
           rng rng
           sparks []]
      (if (= index spark-count)
        [rng sparks]
        (let [[rng spark]
              (new-spark simulation-config rng origin index spark-count)]
          (recur (inc index) rng (conj sparks spark)))))))

(defn advance
  "Advance one spark by dt seconds. All rates are independent of tick count."
  [simulation-config dt {:keys [position velocity life fade-rate] :as spark}]
  (let [[x y] position
        [vx vy] velocity
        gravity (get-in simulation-config [:sparks :gravity])]
    (assoc spark
           :position [(+ x (* vx dt))
                      (+ y (* vy dt))]
           :velocity [vx (+ vy (* gravity dt))]
           :life (- life (* fade-rate dt)))))

(defn advance-all
  "Advance a burst and discard sparks whose lifetime is exhausted."
  [simulation-config dt sparks]
  (into []
        (comp (map #(advance simulation-config dt %))
              (filter alive?))
        sparks))
