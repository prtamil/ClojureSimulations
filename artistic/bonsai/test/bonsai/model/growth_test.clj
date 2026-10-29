;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.model.growth-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [bonsai.model.config :as config]
   [bonsai.model.growth :as growth]))

(defn- construction-context
  [seed style-key]
  {:config config/default-config
   :seed seed
   :style-key style-key
   :style (config/style style-key)})

(defn- calculate-tree
  [seed style-key]
  (let [context (construction-context seed style-key)
        pot (growth/calculate-pot context)
        trunk (growth/calculate-trunk context pot)
        crown (growth/calculate-crown context trunk)]
    {:pot pot
     :tree {:trunk trunk
            :crown crown}}))

(deftest empty-values-document-the-tree-data
  (is (= {:top-row nil} growth/empty-pot))
  (is (= #{:segments :branch-sites :tip}
         (set (keys growth/empty-trunk))))
  (is (= #{:branches :foliage :tips}
         (set (keys growth/empty-crown))))
  (is (= {:trunk growth/empty-trunk
          :crown growth/empty-crown}
         growth/empty-tree)))

(deftest tree-calculation-reads-as-pot-trunk-crown
  (doseq [style-key config/style-order]
    (testing (name style-key)
      (let [first-tree (calculate-tree 42 style-key)
            second-tree (calculate-tree 42 style-key)]
        (is (= first-tree second-tree))
        (is (int? (get-in first-tree [:pot :top-row])))
        (is (seq (get-in first-tree [:tree :trunk :segments])))
        (is (seq (get-in first-tree [:tree :trunk :branch-sites])))
        (is (vector? (get-in first-tree [:tree :trunk :tip])))
        (is (seq (get-in first-tree [:tree :crown :foliage])))
        (is (seq (get-in first-tree [:tree :crown :tips])))))))

(deftest generated-geometry-uses-finite-coordinates
  (let [tree (:tree (calculate-tree 9001 :moyogi))
        trunk (:trunk tree)
        crown (:crown tree)
        segments (concat (:segments trunk) (:branches crown))
        segment-coordinates
        (mapcat #(concat (:start %) (:end %)) segments)
        foliage-coordinates
        (mapcat #(conj (:center %) (:radius %)) (:foliage crown))
        tip-coordinates
        (mapcat :position (:tips crown))
        coordinates
        (concat segment-coordinates foliage-coordinates tip-coordinates)]
    (is (every? #(and (number? %)
                      (Double/isFinite (double %)))
                coordinates))))
