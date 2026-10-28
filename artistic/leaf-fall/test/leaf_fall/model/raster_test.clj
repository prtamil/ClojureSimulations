;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.model.raster-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [leaf-fall.model.config :as config]
   [leaf-fall.model.growth :as growth]
   [leaf-fall.model.raster :as raster]))

(deftest rasterization-is-sparse-and-repeatable
  (let [tree (growth/grow-tree config/default-config 42)
        result (raster/bake-tree config/default-config tree 42)
        cells (:cells result)
        leaves (:leaves result)]
    (is (= result (raster/bake-tree config/default-config tree 42)))
    (testing "only occupied bark coordinates are stored"
      (is (map? cells))
      (is (< (count cells) (config/cell-count config/default-config)))
      (is (every? #(contains? #{:trunk :branch} (:role %)) (vals cells))))
    (testing "leaf templates are valid and do not overwrite bark"
      (is (seq leaves))
      (is (every? #(raster/in-grid? config/default-config
                                   (first (:origin %))
                                   (second (:origin %)))
                  leaves))
      (is (every? #(not (contains? cells (:origin %))) leaves))
      (is (= (count leaves) (count (set (map :origin leaves)))))
      (is (every? #(<= 0.0 (:delay-seconds %)
                           (get-in config/default-config
                                   [:fall :start-delay-max-seconds]))
                  leaves))
      (is (every? (set (get-in config/default-config [:fall :speed-options]))
                  (map :speed leaves))))))

(deftest cell-lookups-are-safe-outside-the-grid
  (let [cells {[1 1] {:row 1 :column 1 :glyph \| :role :trunk}}]
    (is (= \| (:glyph (raster/cell-at config/default-config cells 1 1))))
    (is (nil? (raster/cell-at config/default-config cells -1 1)))
    (is (nil? (raster/cell-at config/default-config cells 1 999)))))
