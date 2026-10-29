;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.model.raster-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [leaf-fall.model.config :as config]
   [leaf-fall.model.core :as model]
   [leaf-fall.model.raster :as raster]))

(defn- place-scene
  [world]
  (-> raster/empty-scene
      (raster/place-trunk world)
      (raster/place-crown world)
      (raster/scatter-leaves world)))

(deftest empty-scene-documents-projected-data
  (is (= {:cells {} :leaf-templates []}
         raster/empty-scene)))

(deftest placement-is-sparse-and-repeatable
  (let [world (model/initial-world 42)
        first-scene (place-scene world)
        second-scene (place-scene world)
        cells (:cells first-scene)
        leaves (:leaf-templates first-scene)]
    (is (= first-scene second-scene))
    (is (= first-scene
           (select-keys world [:cells :leaf-templates])))
    (testing "only occupied bark coordinates are stored"
      (is (map? cells))
      (is (< (count cells) (config/cell-count (:config world))))
      (is (every? #(contains? #{:trunk :branch} (:role %))
                  (vals cells))))
    (testing "leaf templates are valid and do not overwrite bark"
      (is (seq leaves))
      (is (every? #(let [[row column] (:position %)]
                     (raster/in-grid? (:config world) row column))
                  leaves))
      (is (every? #(not (contains? cells (:position %))) leaves))
      (is (= (count leaves)
             (count (set (map :position leaves)))))
      (is (every? #(<= 0.0 (:delay-seconds %)
                           (get-in world
                                   [:config :fall
                                    :start-delay-max-seconds]))
                  leaves))
      (is (every? (set (get-in world [:config :fall :speed-options]))
                  (map :speed leaves))))))

(deftest cell-lookups-are-safe-outside-the-grid
  (let [world (assoc (model/initial-world 42)
                     :cells {[1 1] {:row 1
                                    :column 1
                                    :glyph \|
                                    :role :trunk}})]
    (is (= \| (:glyph (raster/cell-at world 1 1))))
    (is (nil? (raster/cell-at world -1 1)))
    (is (nil? (raster/cell-at world 1 999)))))
