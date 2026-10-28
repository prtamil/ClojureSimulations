;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.utility.noise
  (:require
   [sim.model.config :as config]))

(defn- fade [t]
  (let [t (double t)]
    (* t t t (+ (* t (- (* 6.0 t) 15.0)) 10.0))))

(defn- lerp [a b t]
  (let [a (double a)
        b (double b)
        t (double t)]
    (+ a (* t (- b a)))))

(defn- gradient [hash x y]
  (let [hash (long hash)
        x (double x)
        y (double y)
        h (bit-and hash 7)
        u (if (< h 4) x y)
        v (if (< h 4) y x)]
    (+ (if (zero? (bit-and h 1)) u (- u))
       (if (zero? (bit-and h 2)) (* 2.0 v) (* -2.0 v)))))

(defn perlin-2d
  "Classic improved Perlin gradient noise using a doubled permutation."
  [permutation x y]
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
        n11 (gradient (long (nth permutation (inc b))) (dec local-x) (dec local-y))]
    (lerp (lerp n00 n10 u) (lerp n01 n11 u) v)))

(defn- fbm* [octave-count permutation x y]
  ;; Coerce once at the numerical boundary. The inner loop then stays on JVM
  ;; primitive doubles without exposing mutable arrays or a second fast path.
  (let [octave-count (long octave-count)
        x (double x)
        y (double y)]
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
                           (double
                            (perlin-2d permutation
                                       (* x frequency)
                                       (* y frequency)))))
               (+ max-amplitude amplitude))))))

(defn fbm
  ([permutation x y]
   (fbm config/default-config permutation (double x) (double y)))
  ([simulation-config permutation x y]
   (fbm* (long (get-in simulation-config [:noise :fbm-octaves]))
         permutation (double x) (double y))))

(defn- potential* [octave-count permutation x y field-time]
  (fbm* (long octave-count)
        permutation
        (double x)
        (+ (double y) (double field-time))))

(defn- warped-potential*
  [octave-count amount offset-x offset-y permutation x y field-time]
  (let [octave-count (long octave-count)
        amount (double amount)
        offset-x (double offset-x)
        offset-y (double offset-y)
        x (double x)
        y (double y)
        field-time (double field-time)
        qx (double (fbm* octave-count permutation x (+ y field-time)))
        qy (double
            (fbm* octave-count permutation
                  (+ x offset-x)
                  (+ y offset-y field-time)))]
    (fbm* octave-count permutation
          (+ x (* amount qx))
          (+ y (* amount qy) field-time))))

(defn potential-at
  ([permutation x y field-time]
   (potential-at config/default-config permutation x y field-time))
  ([simulation-config permutation x y field-time]
   (potential* (get-in simulation-config [:noise :fbm-octaves])
               permutation x y field-time)))

(defn warped-potential-at
  ([permutation x y field-time]
   (warped-potential-at config/default-config permutation x y field-time))
  ([simulation-config permutation x y field-time]
   (let [[offset-x offset-y] (get-in simulation-config [:warp :offset])
         amount (get-in simulation-config [:warp :amount])
         octave-count (get-in simulation-config [:noise :fbm-octaves])]
     (warped-potential* octave-count amount offset-x offset-y
                        permutation x y field-time))))

(defn- potential-sample*
  [warped? octave-count amount offset-x offset-y permutation x y field-time]
  (if warped?
    (warped-potential* octave-count amount offset-x offset-y
                       permutation x y field-time)
    (potential* octave-count permutation x y field-time)))

(defn curl-at
  "Return the 2-D curl [dpsi/dy, -dpsi/dx] of the scalar potential."
  ([permutation x y field-time warped?]
   (curl-at config/default-config permutation x y field-time warped?))
  ([simulation-config permutation x y field-time warped?]
   (let [octave-count (long (get-in simulation-config [:noise :fbm-octaves]))
         epsilon (double (get-in simulation-config [:curl :epsilon]))
         [offset-x offset-y] (get-in simulation-config [:warp :offset])
         offset-x (double offset-x)
         offset-y (double offset-y)
         amount (double (get-in simulation-config [:warp :amount]))
         x (double x)
         y (double y)
         field-time (double field-time)
         psi-y+ (double
                 (potential-sample* warped? octave-count amount offset-x offset-y
                                    permutation x (+ y epsilon) field-time))
         psi-y- (double
                 (potential-sample* warped? octave-count amount offset-x offset-y
                                    permutation x (- y epsilon) field-time))
         psi-x+ (double
                 (potential-sample* warped? octave-count amount offset-x offset-y
                                    permutation (+ x epsilon) y field-time))
         psi-x- (double
                 (potential-sample* warped? octave-count amount offset-x offset-y
                                    permutation (- x epsilon) y field-time))
         denominator (* 2.0 epsilon)]
     [(/ (- psi-y+ psi-y-) denominator)
      (- (/ (- psi-x+ psi-x-) denominator))])))

