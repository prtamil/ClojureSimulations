;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.render.glyphs-test
  (:require
   [clojure.test :refer [deftest is]]
   [fireworks.model.core :as model]
   [fireworks.render.glyphs :as glyphs]))

(deftest ascending-rockets-project-to-trail-glyphs
  (let [world (assoc-in (model/initial-world 1)
                        [:show :fireworks]
                        [{:id 0 :stage :flying
                          :position [10.8 12.9]
                          :velocity [0.0 -20.0]
                          :color-index 3}])
        visible (glyphs/project-show world)]
    (is (= [\| \'] (mapv :glyph visible)))
    (is (= [[10 12] [10 13]]
           (mapv (juxt :column :row) visible)))
    (is (:bold? (first visible)))))

(deftest later-sparks-overwrite-earlier-sparks-at-the-same-position
  (let [world (assoc-in (model/initial-world 1)
                        [:show :fireworks]
                        [{:id 0 :stage :bursting
                          :sparks [{:position [2.1 3.1] :glyph \a
                                    :color-index 0 :life 0.8}
                                   {:position [2.8 3.9] :glyph \b
                                    :color-index 1 :life 0.1}]}])
        visible (glyphs/project-show world)]
    (is (= 1 (count visible)))
    (is (= \b (:glyph (first visible))))
    (is (:dim? (first visible)))))

(deftest glyphs-are-semantic-and-clipped-to-the-grid
  (let [world (assoc-in (model/initial-world 1)
                        [:show :fireworks]
                        [{:id 0 :stage :flying
                          :position [-1.0 4.0]
                          :velocity [0.0 -20.0]
                          :color-index 3}
                         {:id 1 :stage :flying
                          :position [5.0 6.0]
                          :velocity [0.0 -20.0]
                          :color-index 2}])
        visible (glyphs/project-show world)]
    (is (= [:rocket :exhaust] (mapv :role visible)))
    (is (every? #(contains? % :color-index) visible))
    (is (= [[5 6] [5 7]] (mapv (juxt :column :row) visible)))))
