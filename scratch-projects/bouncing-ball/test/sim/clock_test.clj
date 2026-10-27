(ns sim.clock-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.clock :as clock]))

(deftest fixed-step-clock-keeps-fractional-remainder
  (let [tick-ns (clock/tick-nanoseconds 60)
        start 1000000000
        elapsed (+ (* 3 tick-ns) (quot tick-ns 2))
        {:keys [clock-state ticks dt]}
        (clock/advance (clock/initial-state start)
                       (+ start elapsed)
                       60
                       (* 100 clock/nanoseconds-per-millisecond))]
    (is (= 3 ticks))
    (is (= (quot tick-ns 2) (:accumulator-ns clock-state)))
    (is (= (/ tick-ns (double clock/nanoseconds-per-second)) dt))))

(deftest fixed-step-clock-caps-long-stalls
  (let [{:keys [ticks]}
        (clock/advance (clock/initial-state 0)
                       (* 5 clock/nanoseconds-per-second)
                       60
                       (* 100 clock/nanoseconds-per-millisecond))]
    (is (= 6 ticks))))
