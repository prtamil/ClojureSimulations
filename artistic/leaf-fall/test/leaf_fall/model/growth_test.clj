;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.model.growth-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [leaf-fall.model.config :as config]
   [leaf-fall.model.growth :as growth]))

(defn- calculate-tree
  [seed]
  (let [settings {:config config/default-config :seed seed}
        trunk (growth/calculate-trunk settings)
        crown (growth/calculate-crown settings trunk)]
    {:trunk trunk
     :crown crown}))

(deftest empty-values-document-tree-data
  (is (= #{:base :top :height :segments :marks}
         (set (keys growth/empty-trunk))))
  (is (= {:branches [] :foliage []}
         growth/empty-crown))
  (is (= {:trunk growth/empty-trunk
          :crown growth/empty-crown}
         growth/empty-tree)))

(deftest seeded-calculation-is-repeatable
  (is (= (calculate-tree 42)
         (calculate-tree 42)))
  (is (not= (calculate-tree 42)
            (calculate-tree 43))))

(deftest calculation-reads-as-trunk-then-crown
  (let [{:keys [trunk crown]} (calculate-tree 42)]
    (testing "trunk data describes its own geometry"
      (is (vector? (:base trunk)))
      (is (vector? (:top trunk)))
      (is (pos-int? (:height trunk)))
      (is (seq (:segments trunk)))
      (is (seq (:marks trunk))))
    (testing "crown data contains recursive branches and foliage"
      (is (seq (:branches crown)))
      (is (seq (:foliage crown))))
    (testing "recursion stays inside the configured depth domain"
      (is (every? #(<= 0 (:depth %)
                       (get-in config/default-config [:tree :max-depth]))
                  (:branches crown)))
      (is (every? pos-int? (map :radius (:foliage crown)))))))

(deftest calculated-geometry-has-finite-coordinates
  (let [{:keys [trunk crown]} (calculate-tree 9001)
        segments (concat (:segments trunk) (:branches crown))
        segment-coordinates
        (mapcat #(concat (:start %) (:end %)) segments)
        mark-coordinates
        (mapcat :position (:marks trunk))
        foliage-coordinates
        (mapcat :center (:foliage crown))
        coordinates
        (concat segment-coordinates mark-coordinates foliage-coordinates)]
    (is (every? #(and (number? %)
                      (Double/isFinite (double %)))
                coordinates))))
