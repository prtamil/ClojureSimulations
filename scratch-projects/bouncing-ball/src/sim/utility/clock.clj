;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.utility.clock)

(def nanoseconds-per-second 1000000000)
(def nanoseconds-per-millisecond 1000000)

(defn tick-nanoseconds [simulation-fps]
  (quot nanoseconds-per-second simulation-fps))

(defn tick-seconds [simulation-fps]
  (/ (tick-nanoseconds simulation-fps)
     (double nanoseconds-per-second)))

(defn initial-state [now-ns]
  {:last-time-ns now-ns
   :accumulator-ns 0})

(defn advance
  "Advance a pure fixed-step clock.

  Returns the fixed ticks earned from real elapsed time, the fixed simulation
  duration, and alpha: the fractional progress toward the next tick.  Renderers
  can use alpha for smooth presentation without feeding variable time into the
  simulation."
  [{:keys [last-time-ns accumulator-ns]} now-ns simulation-fps max-delta-ns]
  (let [elapsed-ns (-> (- now-ns last-time-ns)
                       (max 0)
                       (min max-delta-ns))
        tick-ns (tick-nanoseconds simulation-fps)
        accumulated-ns (+ accumulator-ns elapsed-ns)
        ticks (quot accumulated-ns tick-ns)
        remainder-ns (rem accumulated-ns tick-ns)]
    {:clock-state {:last-time-ns now-ns
                   :accumulator-ns remainder-ns}
     :ticks ticks
     :dt (tick-seconds simulation-fps)
     :alpha (/ remainder-ns (double tick-ns))}))
