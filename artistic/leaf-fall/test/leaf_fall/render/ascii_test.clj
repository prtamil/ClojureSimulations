;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.render.ascii-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [leaf-fall.model.core :as model]
   [leaf-fall.render.ascii :as ascii]))

(deftest headless-render-has-header-and-requested-crop
  (let [text (ascii/render-string (model/initial-world 42)
                                  {:columns 40 :rows 12})
        lines (str/split-lines text)]
    (is (str/includes? (first lines) "phase:DISPLAY"))
    (is (str/includes? (first lines) "seed:42"))
    (is (= 13 (count lines)))
    (is (every? #(= 40 (count %)) (rest lines)))
    (is (some #(re-find #"[|/\\]" %) (rest lines)))))

(deftest reset-phase-renders-a-blank-canvas
  (let [world (assoc (model/initial-world 42) :phase :reset)
        lines (rest (str/split-lines
                     (ascii/render-string world {:columns 20 :rows 5})))]
    (is (every? str/blank? lines))))

(deftest bad-crop-dimensions-are-rejected
  (is (thrown? clojure.lang.ExceptionInfo
               (ascii/render-string (model/initial-world 42)
                                    {:columns 0 :rows 4}))))
