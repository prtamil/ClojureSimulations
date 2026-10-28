;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.model.spark-test
  (:require
   [clojure.test :refer [deftest is]]
   [fireworks.model.config :as config]
   [fireworks.model.spark :as spark]))

(deftest bursts-are-seeded-active-values
  (let [[rng-a sparks-a] (spark/burst config/default-config 42 [12.0 8.0])
        [rng-b sparks-b] (spark/burst config/default-config 42 [12.0 8.0])]
    (is (= rng-a rng-b))
    (is (= sparks-a sparks-b))
    (is (= 80 (count sparks-a)))
    (is (every? #(= [12.0 8.0] (:position %)) sparks-a))
    (is (every? spark/alive? sparks-a))
    (is (= 7 (count (set (map :color-index sparks-a)))))))

(deftest spark-motion-and-fading-use-seconds
  (let [particle {:position [1.0 2.0]
                  :velocity [24.0 -8.0]
                  :life 0.5
                  :fade-rate 1.5
                  :glyph \*
                  :color-index 0}
        advanced (spark/advance config/default-config 0.25 particle)
        one-step (spark/advance config/default-config 0.1 particle)
        two-steps (->> particle
                       (spark/advance config/default-config 0.05)
                       (spark/advance config/default-config 0.05))]
    (is (= [7.0 0.0] (:position advanced)))
    (is (= [24.0 0.0] (:velocity advanced)))
    (is (= 0.125 (:life advanced)))
    (is (< (Math/abs (- (:life one-step) (:life two-steps))) 1.0e-12))))

(deftest expired-sparks-leave-the-active-vector
  (let [spark {:position [0.0 0.0]
               :velocity [0.0 0.0]
               :life 0.01
               :fade-rate 1.0
               :glyph \*
               :color-index 0}]
    (is (empty? (spark/advance-all config/default-config 0.02 [spark])))))
