;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.model.growth-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [bonsai.model.config :as config]
   [bonsai.model.growth :as growth]))

(deftest geometry-is-seeded-and-theme-independent
  (doseq [style-key config/style-order]
    (testing (name style-key)
      (let [first-tree (growth/grow-tree config/default-config 42 style-key)
            second-tree (growth/grow-tree config/default-config 42 style-key)]
        (is (= first-tree second-tree))
        (is (seq (get-in first-tree [:tree :segments])))
        (is (seq (get-in first-tree [:tree :foliage])))
        (is (seq (get-in first-tree [:tree :tips])))
        (is (int? (:pot-top first-tree)))))))

(deftest generated-geometry-uses-finite-coordinates
  (let [tree (:tree (growth/grow-tree config/default-config 9001 :moyogi))
        coordinates
        (concat
         (mapcat #(map % [:x0 :y0 :x1 :y1]) (:segments tree))
         (mapcat #(map % [:cx :cy :radius]) (:foliage tree))
         (mapcat #(map % [:x :y]) (:tips tree)))]
    (is (every? #(and (number? %) (Double/isFinite (double %))) coordinates))))
