;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.utility.random)

(def ^:private ^:const gamma -7046029254386353131)
(def ^:private ^:const mix-factor-1 -4658895280553007687)
(def ^:private ^:const mix-factor-2 -7723592293110705685)
(def ^:private ^:const two-to-the-53 9007199254740992.0)

(defn- mix64 [value]
  (let [value (long value)
        value (unchecked-multiply
               (bit-xor value (unsigned-bit-shift-right value 30))
               mix-factor-1)
        value (unchecked-multiply
               (bit-xor value (unsigned-bit-shift-right value 27))
               mix-factor-2)]
    (bit-xor value (unsigned-bit-shift-right value 31))))

(defn next-long [state]
  (let [state' (unchecked-add (long state) gamma)]
    [state' (mix64 state')]))

(defn next-int [state bound]
  (when-not (pos-int? bound)
    (throw (ex-info "Random bound must be a positive integer" {:bound bound})))
  (let [[state' value] (next-long state)]
    [state' (int (Long/remainderUnsigned value (long bound)))]))

(defn next-double [state]
  (let [[state' value] (next-long state)]
    [state' (/ (double (unsigned-bit-shift-right value 11)) two-to-the-53)]))

