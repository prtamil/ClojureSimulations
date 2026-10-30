;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.render.ascii-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [leaf-fall.model.core :as model]
   [leaf-fall.render.ascii :as ascii])
  (:import
   [java.nio.charset StandardCharsets]
   [java.security MessageDigest]
   [java.util HexFormat]))

(defn- sha-256
  [text]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (.formatHex (HexFormat/of)
                (.digest digest
                         (.getBytes text StandardCharsets/UTF_8)))))

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

(deftest seeded-display-and-falling-frames-match-their-snapshots
  (let [display (model/initial-world 42)
        falling (model/advance-world-steps
                 (model/skip-phase display)
                 120
                 (/ 1.0 60.0))]
    (is (= "3d1d62ff878642ca62be58f1d2eaed7317b84367fba344cfabf9fbe1836cbc3d"
           (sha-256 (ascii/render-string display))))
    (is (= "dff6e8313d3c3f916072c276b2b9046ba09990aba643aafa1aadc537e5f9d5ef"
           (sha-256 (ascii/render-string falling))))))
