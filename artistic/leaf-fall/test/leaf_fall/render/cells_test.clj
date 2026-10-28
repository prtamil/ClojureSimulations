;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.render.cells-test
  (:require
   [clojure.test :refer [deftest is]]
   [leaf-fall.model.core :as model]
   [leaf-fall.render.cells :as cells]))

(deftest projection-keeps-rendering-out-of-the-model
  (let [display (model/initial-world 42)
        falling (model/skip-phase display)
        reset-world (assoc display :phase :reset)]
    (is (some #(= :leaf (:role %)) (cells/visible-cells display)))
    (is (some #(= :leaf (:role %)) (cells/visible-cells falling)))
    (is (empty? (cells/visible-cells reset-world)))))
