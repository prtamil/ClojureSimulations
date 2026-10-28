;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.utility.random)

;; Explicit 32-bit state makes the particle system pure and repeatable.
(def ^:private u32-mask 0xffffffff)
(def ^:private unit-divisor 16777216.0)

(defn u32 [value]
  (bit-and (long value) u32-mask))

(defn next-u32
  "Return [next-state random-u32] from a small deterministic LCG."
  [state]
  (let [next-state (u32 (+ (* (u32 state) 1664525) 1013904223))]
    [next-state next-state]))

(defn unit
  "Return [next-state value], where value is in [0, 1)."
  [state]
  (let [[next-state value] (next-u32 state)]
    [next-state (/ (unsigned-bit-shift-right value 8) unit-divisor)]))

(defn between
  "Return [next-state value], where value is in [low, high)."
  [state low high]
  (when-not (< low high)
    (throw (ex-info "Random range requires low < high"
                    {:low low :high high})))
  (let [[next-state value] (unit state)]
    [next-state (+ low (* (- high low) value))]))

(defn index
  "Return [next-state integer], where the integer is in [0, bound)."
  [state bound]
  (when-not (pos-int? bound)
    (throw (ex-info "Random bound must be a positive integer" {:bound bound})))
  (let [[next-state value] (next-u32 state)]
    [next-state (int (mod value bound))]))
