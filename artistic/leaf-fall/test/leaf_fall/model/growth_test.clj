;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.model.growth-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [leaf-fall.model.config :as config]
   [leaf-fall.model.growth :as growth]))

(deftest seeded-growth-is-repeatable
  (is (= (growth/grow-tree config/default-config 42)
         (growth/grow-tree config/default-config 42)))
  (is (not= (growth/grow-tree config/default-config 42)
            (growth/grow-tree config/default-config 43))))

(deftest growth-produces-renderer-independent-tree-geometry
  (let [{:keys [segments marks foliage] :as tree}
        (growth/grow-tree config/default-config 42)]
    (testing "the result is geometry, not a canvas or particle pool"
      (is (= #{:segments :marks :foliage} (set (keys tree))))
      (is (not (contains? tree :canvas)))
      (is (not (contains? tree :leaves))))
    (testing "the tree contains a trunk, branches, details, and foliage"
      (is (some #(= :trunk (:role %)) segments))
      (is (some #(= :branch (:role %)) segments))
      (is (seq marks))
      (is (seq foliage)))
    (testing "recursion stays inside the configured depth domain"
      (is (every? #(<= 0 (:depth %) (get-in config/default-config
                                             [:tree :max-depth]))
                  segments))
      (is (every? pos-int? (map :radius foliage))))))
