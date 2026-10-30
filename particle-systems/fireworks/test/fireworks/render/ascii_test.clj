;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.render.ascii-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [fireworks.model.config :as config]
   [fireworks.model.core :as model]
   [fireworks.render.ascii :as ascii])
  (:import
   (java.math BigInteger)
   (java.security MessageDigest)))

(defn- sha-256 [text]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (.update digest (.getBytes text "UTF-8"))
    (format "%064x" (BigInteger. 1 (.digest digest)))))

(deftest ascii-render-is-pure-and-has-one-line-per-grid-row
  (let [world (model/advance-world (model/initial-world 42))
        before world
        rendered (ascii/render-string world)
        lines (str/split-lines rendered)]
    (is (= before world))
    (is (str/starts-with? (first lines) "FIREWORKS"))
    (is (= (inc (config/grid-height (:config world))) (count lines)))
    (is (every? #(= (config/grid-width (:config world)) (count %))
                (rest lines)))
    (is (some #(str/includes? % "|") (rest lines)))))

(deftest seeded-ascii-frames-keep-the-original-pictures
  (let [expected {1 "d09de68d2554da0fc4ffe458a9de900d59b238b462c2e795f54e1b40feb836c6"
                  45 "b468814114bb76ea292c0bcc0952cde027ddb430cc437cd7483ea1127364d145"
                  90 "556ef73beb91e9737bea6a8abdc2caf13cee67f4bb59193e3c331655ab195bc8"
                  180 "90f56bc7ed2861fdec728b8bd3d8f2bb77743ec79d6a341310366e304faae599"}]
    (doseq [[steps snapshot] expected]
      (let [world (model/advance-world-steps (model/initial-world 42)
                                             steps
                                             (/ 1.0 30.0))]
        (is (= snapshot (sha-256 (ascii/render-string world)))
            (str "ASCII snapshot after " steps " steps"))))))
