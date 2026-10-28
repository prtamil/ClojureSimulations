;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.utility.random)

(def ^:private u32-mask 0xffffffff)
(def ^:private unit-divisor 16777216.0)

(defn u32
  "Keep the low 32 bits as a non-negative long."
  [value]
  (bit-and (long value) u32-mask))

(defn next-u32
  "Return [next-state value] from an explicit deterministic stream."
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
  "Return [next-state integer] where the integer is in [0, bound)."
  [state bound]
  (when-not (pos-int? bound)
    (throw (ex-info "Random bound must be a positive integer" {:bound bound})))
  (let [[next-state value] (next-u32 state)]
    [next-state (int (mod value bound))]))

(defn next-tree-seed
  "Derive a repeatable but visibly different seed for a later tree cycle."
  [seed cycle]
  (second (next-u32 (bit-xor (u32 seed)
                             (u32 (* (long cycle) 0x9e3779b9))))))

(defn derive-state
  "Derive an independent deterministic stream from a seed and salt."
  [seed salt]
  (second (next-u32 (bit-xor (u32 seed) (u32 salt)))))
