;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.render.cells-test
  (:require
   [clojure.test :refer [deftest is]]
   [bonsai.model.core :as model]
   [bonsai.render.cells :as cells]))

(deftest growth-reveal-is-a-frame-level-projection
  (let [grown (model/initial-world 42)
        starting (model/replay-growth grown)
        visible-at-start (cells/visible-cells starting)
        visible-when-grown (cells/visible-cells grown)]
    (is (= (count (:cells grown)) (count visible-when-grown)))
    (is (< (count visible-at-start) (count visible-when-grown)))
    (is (every? #(= :pot (:role %))
                (filter #(= :pot (:role %)) visible-at-start)))))

(deftest wind-animation-is-deterministic-for-a-world-value
  (let [world (model/advance-world-steps (model/initial-world 42)
                                         60 (/ 1.0 30.0))
        foliage (first (filter #(= :foliage (:role %))
                               (vals (:cells world))))]
    (is foliage)
    (is (= (cells/display-glyph world foliage)
           (cells/display-glyph world foliage)))))
