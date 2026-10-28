;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.utility.random-test
  (:require
   [clojure.test :refer [deftest is]]
   [bonsai.utility.random :as random]))

(deftest lcg-matches-the-c-generator
  (is (= [1013904223 1013904223] (random/next-u32 0)))
  (is (= 1196435762 (second (random/next-u32 1013904223)))))

(deftest random-stream-is-explicit-and-bounded
  (let [[state-a value-a] (random/unit 42)
        [state-b value-b] (random/unit 42)
        [_ index] (random/index state-a 6)]
    (is (= [state-a value-a] [state-b value-b]))
    (is (<= 0.0 value-a))
    (is (< value-a 1.0))
    (is (<= 0 index 5))))

(deftest hash-is-deterministic-u32
  (is (= (random/hash3 12 7 3) (random/hash3 12 7 3)))
  (is (<= 0 (random/hash3 -1 999 42) 0xffffffff)))
