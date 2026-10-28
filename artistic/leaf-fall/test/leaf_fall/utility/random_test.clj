;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.utility.random-test
  (:require
   [clojure.test :refer [deftest is]]
   [leaf-fall.utility.random :as random]))

(deftest lcg-matches-the-c-recurrence
  (is (= [1083814273 1083814273] (random/next-u32 42)))
  (is (= [378494188 378494188] (random/next-u32 1083814273))))

(deftest random-helpers-are-repeatable-and-bounded
  (is (= (random/unit 99) (random/unit 99)))
  (let [[_ value] (random/unit 99)
        [_ index] (random/index 99 7)]
    (is (<= 0.0 value 1.0))
    (is (<= 0 index 6)))
  (is (thrown? clojure.lang.ExceptionInfo (random/index 1 0))))
