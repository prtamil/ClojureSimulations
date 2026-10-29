;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.model.leaf-test
  (:require
   [clojure.test :refer [deftest is]]
   [leaf-fall.model.leaf :as leaf]))

(def template
  {:position [4 7] :glyph \* :delay-seconds 0.25 :speed 10.0})

(deftest elapsed-time-motion-consumes-delay-before-distance
  (let [particle (leaf/start template)
        waiting (leaf/advance particle 0.10)
        moving (leaf/advance waiting 0.20)]
    (is (leaf/waiting? waiting))
    (is (= [4.0 7] (:position waiting)))
    (is (false? (leaf/waiting? moving)))
    (is (< (Math/abs (- 4.5 (first (:position moving)))) 1.0e-9))
    (is (= 4 (leaf/head-row moving)))))

(deftest complete-trails-are-removed
  (let [particle (leaf/start (assoc template
                                    :delay-seconds 0.0
                                    :speed 20.0))]
    (is (= 1 (count (leaf/advance-leaves [particle] 0.1 10 2))))
    (is (empty? (leaf/advance-leaves [particle] 1.0 10 2)))))
