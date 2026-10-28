;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.model.raster-test
  (:require
   [clojure.test :refer [deftest is]]
   [bonsai.model.config :as config]
   [bonsai.model.growth :as growth]
   [bonsai.model.raster :as raster]
   [bonsai.utility.theme :as theme]))

(defn- with-role [cells role]
  (filter #(= role (:role %)) (vals cells)))

(deftest rasterization-produces-a-sparse-coordinate-map
  (let [{:keys [tree pot-top]}
        (growth/grow-tree config/default-config 42 :moyogi)
        cells (raster/bake-tree config/default-config tree pot-top
                                (first theme/themes) 42)]
    (is (map? cells))
    (is (< (count cells) (config/cell-count config/default-config)))
    (is (every? (fn [[[row column] cell]]
                  (= [row column] [(:row cell) (:column cell)]))
                cells))
    (is (seq (with-role cells :trunk-dark)))
    (is (seq (with-role cells :foliage)))
    (is (seq (with-role cells :pot)))))

(deftest winter-presentation-hides-leaves-and-adds-snow
  (let [{:keys [tree pot-top]}
        (growth/grow-tree config/default-config 42 :moyogi)
        winter (nth theme/themes 3)
        cells (raster/bake-tree config/default-config tree pot-top winter 42)]
    (is (seq (:foliage tree)))
    (is (empty? (with-role cells :foliage)))
    (is (seq (with-role cells :snow-tip)))
    (is (every? #(= \* (:glyph %)) (with-role cells :snow-tip)))))
