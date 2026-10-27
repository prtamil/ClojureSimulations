;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.clock)

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
  "Advance a fixed-step clock and return its new state, earned ticks, and dt.
  Elapsed wall time is capped so waking from a stall cannot cause an enormous
  catch-up burst. This function is pure; callers decide how to run the ticks."
  [{:keys [last-time-ns accumulator-ns]} now-ns simulation-fps max-delta-ns]
  (let [elapsed-ns (-> (- now-ns last-time-ns)
                       (max 0)
                       (min max-delta-ns))
        tick-ns (tick-nanoseconds simulation-fps)
        accumulated-ns (+ accumulator-ns elapsed-ns)
        ticks (quot accumulated-ns tick-ns)]
    {:clock-state {:last-time-ns now-ns
                   :accumulator-ns (rem accumulated-ns tick-ns)}
     :ticks ticks
     :dt (tick-seconds simulation-fps)}))

