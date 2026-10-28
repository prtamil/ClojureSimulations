;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.utility.random)

(def ^:private u32-mask 0xffffffff)
(def ^:private unit-divisor 16777216.0)

(defn u32
  "Keep the low 32 bits of value as a non-negative long."
  [value]
  (bit-and (long value) u32-mask))

(defn next-u32
  "Return [next-state random-u32] from an explicit deterministic stream."
  [state]
  (let [next-state (u32 (+ (* (u32 state) 1664525) 1013904223))]
    [next-state next-state]))

(defn unit
  "Return [next-state value], with value in [0, 1)."
  [state]
  (let [[next-state value] (next-u32 state)]
    [next-state (/ (unsigned-bit-shift-right value 8) unit-divisor)]))

(defn between
  "Return [next-state value], with value in [low, high)."
  [state low high]
  (let [[next-state value] (unit state)]
    [next-state (+ low (* (- high low) value))]))

(defn index
  "Return [next-state integer], where the integer is in [0, bound)."
  [state bound]
  (when-not (pos-int? bound)
    (throw (ex-info "Random bound must be a positive integer" {:bound bound})))
  (let [[next-state value] (next-u32 state)]
    [next-state (int (mod value bound))]))

(defn hash3
  "Mix integer coordinates and time into a deterministic 32-bit value."
  [x y time-key]
  (let [h (u32 (+ (* (u32 x) 374761393)
                    (* (u32 y) 668265263)
                    (* (u32 time-key) 2147483647)))
        h (u32 (* (bit-xor h (unsigned-bit-shift-right h 13))
                  1274126177))]
    (u32 (bit-xor h (unsigned-bit-shift-right h 16)))))
