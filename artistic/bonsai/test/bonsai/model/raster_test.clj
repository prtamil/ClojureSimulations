;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.model.raster-test
  (:require
   [clojure.test :refer [deftest is]]
   [bonsai.model.config :as config]
   [bonsai.model.core :as model]
   [bonsai.model.raster :as raster]))

(defn- with-role
  [cells role]
  (filter #(= role (:role %)) (vals cells)))

(defn- place-tree
  [world]
  (-> {}
      (raster/place-trunk world)
      (raster/place-crown world)
      (raster/place-pot world)))

(deftest rasterization-reads-as-trunk-crown-pot
  (let [world (model/initial-world 42)
        cells (place-tree world)]
    (is (= (:cells world) cells))
    (is (< (count cells) (config/cell-count (:config world))))
    (is (every? (fn [[[row column] cell]]
                  (= [row column] [(:row cell) (:column cell)]))
                cells))
    (is (seq (with-role cells :trunk-dark)))
    (is (seq (with-role cells :foliage)))
    (is (seq (with-role cells :pot)))))

(deftest winter-presentation-hides-leaves-and-adds-snow
  (let [spring (model/initial-world 42)
        winter (model/cycle-theme spring 3)]
    (is (seq (get-in winter [:tree :crown :foliage])))
    (is (empty? (with-role (:cells winter) :foliage)))
    (is (seq (with-role (:cells winter) :snow-tip)))
    (is (every? #(= \* (:glyph %))
                (with-role (:cells winter) :snow-tip)))))
