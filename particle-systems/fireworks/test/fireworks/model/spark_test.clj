;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.model.spark-test
  (:require
   [clojure.test :refer [deftest is]]
   [fireworks.model.config :as config]
   [fireworks.model.spark :as spark]))

(def sample-spark
  {:position [1.0 2.0]
   :velocity [24.0 -8.0]
   :life 0.5
   :fade-rate 1.5
   :glyph \*
   :color-index 0})

(deftest bursts-are-seeded-active-values
  (let [[state-a sparks-a]
        (spark/make-burst config/default-config 42 [12.0 8.0])
        [state-b sparks-b]
        (spark/make-burst config/default-config 42 [12.0 8.0])]
    (is (= state-a state-b))
    (is (= sparks-a sparks-b))
    (is (= 80 (count sparks-a)))
    (is (= #{:position :velocity :life :fade-rate :glyph :color-index}
           (set (keys (first sparks-a)))))
    (is (every? #(= [12.0 8.0] (:position %)) sparks-a))
    (is (every? spark/alive? sparks-a))
    (is (= 7 (count (set (map :color-index sparks-a)))))))

(deftest spark-motion-and-fading-use-seconds
  (let [advanced (spark/move-spark config/default-config 0.25 sample-spark)
        one-step (spark/move-spark config/default-config 0.1 sample-spark)
        two-steps (->> sample-spark
                       (spark/move-spark config/default-config 0.05)
                       (spark/move-spark config/default-config 0.05))]
    (is (= [7.0 0.0] (:position advanced)))
    (is (= [24.0 0.0] (:velocity advanced)))
    (is (= 0.125 (:life advanced)))
    (is (< (Math/abs (- (:life one-step) (:life two-steps))) 1.0e-12))))

(deftest expired-sparks-leave-the-active-vector
  (let [dying (assoc sample-spark :life 0.01 :fade-rate 1.0)]
    (is (empty? (spark/move-sparks config/default-config 0.02 [dying])))))
