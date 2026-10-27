(ns sim.clock-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.clock :as clock]))

(deftest fixed-step-clock-keeps-remainder
  (let [tick-ns (clock/tick-nanoseconds 60)
        elapsed (+ (* 3 tick-ns) (quot tick-ns 2))
        {:keys [clock-state ticks]}
        (clock/advance (clock/initial-state 0) elapsed 60
                       (* 100 clock/nanoseconds-per-millisecond))]
    (is (= 3 ticks))
    (is (= (quot tick-ns 2) (:accumulator-ns clock-state)))))

(deftest fixed-step-clock-caps-stalls
  (let [{:keys [ticks]}
        (clock/advance (clock/initial-state 0)
                       (* 5 clock/nanoseconds-per-second)
                       60
                       (* 100 clock/nanoseconds-per-millisecond))]
    (is (= 6 ticks))))
