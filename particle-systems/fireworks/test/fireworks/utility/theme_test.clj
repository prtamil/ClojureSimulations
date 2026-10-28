;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.utility.theme-test
  (:require
   [clojure.test :refer [deftest is]]
   [fireworks.model.core :as model]
   [fireworks.utility.theme :as theme]))

(deftest c-palettes-are-present-and-cyclic
  (is (= ["matrix" "neon" "nova" "ocean" "fire"
          "toxic" "gold" "ice" "aurora" "plasma"]
         (mapv :name theme/themes)))
  (is (every? #(= 7 (count (:spark-colors %))) theme/themes))
  (is (= 9 (theme/cycle-index 0 -1)))
  (is (= 0 (theme/cycle-index 9 1))))

(deftest xterm-color-conversion-is-stable
  (is (= [0 175 0] (theme/xterm-rgb 34)))
  (is (= [238 238 238] (theme/xterm-rgb 255)))
  (is (= [0 135 0] (theme/spark-rgb (model/initial-world 1) 0))))
