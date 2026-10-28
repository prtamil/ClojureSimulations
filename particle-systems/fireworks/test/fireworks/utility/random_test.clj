;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.utility.random-test
  (:require
   [clojure.test :refer [deftest is]]
   [fireworks.utility.random :as random]))

(deftest random-stream-is-explicit-and-repeatable
  (is (= (take 8 (iterate (comp first random/next-u32) 42))
         (take 8 (iterate (comp first random/next-u32) 42))))
  (let [[state-a value-a] (random/unit 42)
        [state-b value-b] (random/unit 42)]
    (is (= state-a state-b))
    (is (= value-a value-b))
    (is (<= 0.0 value-a))
    (is (< value-a 1.0))))

(deftest bounded-values-stay-in-range
  (loop [rng 7
         remaining 100]
    (when (pos? remaining)
      (let [[rng value] (random/index rng 7)]
        (is (<= 0 value 6))
        (recur rng (dec remaining))))))
