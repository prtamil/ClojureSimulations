;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.utility.random)

;; SplitMix64 is small, deterministic, and has well-mixed low bits. The state
;; is explicit, so callers remain pure and reproducible.

(def ^:private ^:const gamma -7046029254386353131)
(def ^:private ^:const mix-factor-1 -4658895280553007687)
(def ^:private ^:const mix-factor-2 -7723592293110705685)

(defn- mix64 [value]
  (let [value (long value)
        value (unchecked-multiply
               (bit-xor value (unsigned-bit-shift-right value 30))
               mix-factor-1)
        value (unchecked-multiply
               (bit-xor value (unsigned-bit-shift-right value 27))
               mix-factor-2)]
    (bit-xor value (unsigned-bit-shift-right value 31))))

(defn next-long
  "Return [next-state random-long] from an explicit SplitMix64 state."
  [state]
  (let [state (long state)
        state' (unchecked-add state gamma)]
    [state' (mix64 state')]))

(defn next-int
  "Return [next-state integer], where the integer is in [0, bound)."
  [state bound]
  (when-not (pos-int? bound)
    (throw (ex-info "Random bound must be a positive integer"
                    {:bound bound})))
  (let [[state' value] (next-long state)]
    [state' (int (Long/remainderUnsigned value (long bound)))]))

(defn unit
  "Return [next-state value], where value is uniformly distributed in [0, 1)."
  [state]
  (let [[state' value] (next-long state)]
    [state' (/ (double (unsigned-bit-shift-right value 11))
               9007199254740992.0)]))

(defn between
  "Return [next-state value], where value is uniformly distributed in [low, high)."
  [state low high]
  (when-not (< low high)
    (throw (ex-info "Random range requires low < high"
                    {:low low :high high})))
  (let [[state' value] (unit state)]
    [state' (+ low (* value (- high low)))]))

(defn shuffled-permutation
  "Return [next-state doubled-permutation] after a Fisher-Yates shuffle."
  [state]
  (loop [index 255
         state state
         permutation (vec (range 256))]
    (if (zero? index)
      [state (vec (concat permutation permutation))]
      (let [[state' swap-index] (next-int state (inc index))
            current (nth permutation index)
            swap-value (nth permutation swap-index)]
        (recur (dec index)
               state'
               (assoc permutation
                      index swap-value
                      swap-index current))))))

