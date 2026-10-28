;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.utility.clock)

(def nanoseconds-per-second 1000000000)
(def nanoseconds-per-millisecond 1000000)

(defn initial-state [now-ns]
  {:last-ns now-ns :accumulator-ns 0})

(defn advance
  "Accumulate wall time and return the fixed simulation ticks it earned."
  [{:keys [last-ns accumulator-ns]} now-ns simulation-fps max-delta-ns]
  (let [frame-delta (max 0 (min max-delta-ns (- now-ns last-ns)))
        tick-ns (quot nanoseconds-per-second simulation-fps)
        accumulated (+ accumulator-ns frame-delta)
        ticks (quot accumulated tick-ns)
        remainder-ns (mod accumulated tick-ns)]
    {:clock-state {:last-ns now-ns
                   :accumulator-ns remainder-ns}
     :ticks ticks
     :dt (/ 1.0 simulation-fps)
     :alpha (/ remainder-ns (double tick-ns))}))
