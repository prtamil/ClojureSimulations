;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.utility.clock-test
  (:require
   [clojure.test :refer [deftest is]]
   [leaf-fall.utility.clock :as clock]))

(deftest fixed-step-clock-accumulates-and-caps-time
  (let [initial (clock/initial-state 1000)
        half-tick (quot clock/nanoseconds-per-second 120)
        first-result (clock/advance initial (+ 1000 half-tick) 60
                                    clock/nanoseconds-per-second)
        second-result (clock/advance (:clock-state first-result)
                                     (+ 1000 (* half-tick 4))
                                     60 clock/nanoseconds-per-second)]
    (is (zero? (:ticks first-result)))
    (is (< 0.0 (:alpha first-result) 1.0))
    (is (= 2 (:ticks second-result))))
  (let [result (clock/advance (clock/initial-state 0)
                              (* 10 clock/nanoseconds-per-second)
                              60
                              (* 100 clock/nanoseconds-per-millisecond))]
    (is (= 6 (:ticks result)))))
