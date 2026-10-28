;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.render.batch-test
  (:require
   [clojure.test :refer [deftest is]]
   [leaf-fall.render.batch :as batch]))

(deftest cells-are-batched-by-role-and-row
  (let [rows (batch/cell-rows
              3 5
              [{:role :trunk :row 2 :column 1 :glyph \|}
               {:role :leaf :row 0 :column 2 :glyph \*}
               {:role :leaf :row 0 :column 4 :glyph \o}
               {:role :fall-head :row 1 :column 3 :glyph \@}])]
    (is (= [{:role :trunk :row 2 :text " |   "}
            {:role :leaf :row 0 :text "  * o"}
            {:role :fall-head :row 1 :text "   @ "}]
           rows))))

(deftest batching-clips-invalid-and-unknown-cells
  (is (= []
         (batch/cell-rows
          2 2
          [{:role :unknown :row 0 :column 0 :glyph \x}
           {:role :leaf :row -1 :column 0 :glyph \x}
           {:role :leaf :row 0 :column 5 :glyph \x}]))))
