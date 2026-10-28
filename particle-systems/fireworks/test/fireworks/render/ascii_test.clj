;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.render.ascii-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [fireworks.model.config :as config]
   [fireworks.model.core :as model]
   [fireworks.render.ascii :as ascii]))

(deftest ascii-render-is-pure-and-has-one-line-per-grid-row
  (let [world (model/update-world (model/initial-world 42))
        before world
        rendered (ascii/render-string world)
        lines (str/split-lines rendered)]
    (is (= before world))
    (is (str/starts-with? (first lines) "FIREWORKS"))
    (is (= (inc (config/grid-height (:config world))) (count lines)))
    (is (every? #(= (config/grid-width (:config world)) (count %))
                (rest lines)))
    (is (some #(str/includes? % "|") (rest lines)))))
