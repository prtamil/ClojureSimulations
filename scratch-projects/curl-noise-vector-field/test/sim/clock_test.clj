(ns sim.clock-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.clock :as clock]))

(deftest fixed-step-clock-earns-whole-ticks-and-keeps-remainder
  (let [tick-ns (quot clock/nanoseconds-per-second 60)
        start 1000000000
        initial (clock/initial-state start)
        elapsed (+ (* 3 tick-ns) (quot tick-ns 2))
        {:keys [clock-state ticks dt]}
        (clock/advance initial (+ start elapsed) 60 (* 100 clock/nanoseconds-per-millisecond))]
    (is (= 3 ticks))
    (is (= (quot tick-ns 2) (:accumulator-ns clock-state)))
    (is (= (+ start elapsed) (:last-time-ns clock-state)))
    (is (= (/ tick-ns (double clock/nanoseconds-per-second)) dt))))

(deftest fixed-step-clock-caps-long-stalls
  (let [initial (clock/initial-state 0)
        max-delta-ns (* 100 clock/nanoseconds-per-millisecond)
        {:keys [ticks]} (clock/advance initial
                                       (* 5 clock/nanoseconds-per-second)
                                       60
                                       max-delta-ns)]
    (is (= 6 ticks))))


