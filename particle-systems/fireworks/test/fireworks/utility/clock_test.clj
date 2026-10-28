;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.utility.clock-test
  (:require
   [clojure.test :refer [deftest is]]
   [fireworks.utility.clock :as clock]))

(deftest fixed-step-clock-accumulates-and-caps-time
  (let [start (clock/initial-state 0)
        one-frame (clock/advance start 16666667 60 100000000)
        stalled (clock/advance (:clock-state one-frame)
                               1016666667 60 100000000)]
    (is (= 1 (:ticks one-frame)))
    (is (< (Math/abs (- (/ 1.0 60.0) (:dt one-frame))) 1.0e-12))
    (is (< 0.0 (:alpha one-frame) 1.0))
    (is (<= (:ticks stalled) 7))))
