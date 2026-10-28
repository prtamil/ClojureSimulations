;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.render.ascii-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [bonsai.model.core :as model]
   [bonsai.render.ascii :as ascii]))

(deftest headless-render-has-header-and-requested-shape
  (let [text (ascii/render-string (model/initial-world 42)
                                  {:columns 60 :rows 24})
        lines (str/split-lines text)]
    (is (= 25 (count lines)))
    (is (str/includes? (first lines) "style:MOYOGI"))
    (is (str/includes? (first lines) "theme:SPRING"))
    (is (every? #(= 60 (count %)) (rest lines)))
    (is (some #(str/includes? % "___") (rest lines)))))
