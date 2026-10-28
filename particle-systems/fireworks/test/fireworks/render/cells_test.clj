;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.render.cells-test
  (:require
   [clojure.test :refer [deftest is]]
   [fireworks.model.core :as model]
   [fireworks.render.cells :as cells]))

(deftest ascending-rockets-project-to-trail-cells
  (let [world (assoc (model/initial-world 1)
                     :rockets [{:id 0 :phase :ascending
                                :position [10.8 12.9]
                                :velocity [0.0 -20.0]
                                :color-index 3}])
        visible (cells/visible-cells world)]
    (is (= [\| \'] (mapv :glyph visible)))
    (is (= [[10 12] [10 13]]
           (mapv (juxt :column :row) visible)))
    (is (:bold? (first visible)))))

(deftest later-sparks-overwrite-earlier-sparks-in-the-same-cell
  (let [world (assoc (model/initial-world 1)
                     :rockets
                     [{:id 0 :phase :bursting
                       :sparks [{:position [2.1 3.1] :glyph \a
                                 :color-index 0 :life 0.8}
                                {:position [2.8 3.9] :glyph \b
                                 :color-index 1 :life 0.1}]}])
        visible (cells/visible-cells world)]
    (is (= 1 (count visible)))
    (is (= \b (:glyph (first visible))))
    (is (:dim? (first visible)))))
