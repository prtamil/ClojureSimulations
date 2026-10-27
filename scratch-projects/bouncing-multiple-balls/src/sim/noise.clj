;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.noise
  (:require
   [sim.config :as config]))

(defn- fade [t]
  (let [t (double t)]
    (* t t t (+ (* t (- (* 6.0 t) 15.0)) 10.0))))

(defn- lerp [a b t]
  (+ (double a) (* (double t) (- (double b) (double a)))))

(defn- gradient [hash x y]
  (let [h (bit-and (long hash) 7)
        x (double x)
        y (double y)
        u (if (< h 4) x y)
        v (if (< h 4) y x)]
    (+ (if (zero? (bit-and h 1)) u (- u))
       (if (zero? (bit-and h 2)) (* 2.0 v) (* -2.0 v)))))

(defn perlin-2d [permutation x y]
  (let [x (double x)
        y (double y)
        floor-x (Math/floor x)
        floor-y (Math/floor y)
        grid-x (bit-and (long floor-x) 255)
        grid-y (bit-and (long floor-y) 255)
        local-x (- x floor-x)
        local-y (- y floor-y)
        u (fade local-x)
        v (fade local-y)
        a (+ (long (nth permutation grid-x)) grid-y)
        b (+ (long (nth permutation (inc grid-x))) grid-y)
        n00 (gradient (long (nth permutation a)) local-x local-y)
        n10 (gradient (long (nth permutation b)) (dec local-x) local-y)
        n01 (gradient (long (nth permutation (inc a))) local-x (dec local-y))
        n11 (gradient (long (nth permutation (inc b)))
                      (dec local-x) (dec local-y))]
    (lerp (lerp n00 n10 u) (lerp n01 n11 u) v)))

(defn- fbm* [octave-count permutation x y]
  (loop [octave 0
         amplitude 1.0
         frequency 1.0
         total 0.0
         max-amplitude 0.0]
    (if (= octave octave-count)
      (/ total max-amplitude)
      (recur (inc octave)
             (* amplitude 0.5)
             (* frequency 2.0)
             (+ total (* amplitude
                         (perlin-2d permutation
                                    (* (double x) frequency)
                                    (* (double y) frequency))))
             (+ max-amplitude amplitude)))))

(defn fbm
  ([permutation x y]
   (fbm config/default-config permutation x y))
  ([simulation-config permutation x y]
   (fbm* (get-in simulation-config [:noise :fbm-octaves])
         permutation x y)))

(defn sample-2d
  ([permutation x y]
   (sample-2d config/default-config permutation x y))
  ([simulation-config permutation x y]
   (let [scale (get-in simulation-config [:noise :scale])]
     (fbm simulation-config permutation (* scale x) (* scale y)))))
