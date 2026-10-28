;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.render.quil-test
  (:require
   [clojure.test :refer [deftest is]]
   [quil.core :as q]
   [leaf-fall.app.runtime :as runtime]
   [leaf-fall.render.quil :as quil]))

(deftest quit-key-releases-the-gui-before-requesting-exit
  (let [exit-called? (atom false)]
    (reset! runtime/gui-running? true)
    (try
      (with-redefs [q/exit #(reset! exit-called? true)]
        (is (= :renderer-state
               (quil/key-pressed :renderer-state {:raw-key \q}))))
      (is (false? @runtime/gui-running?))
      (is @exit-called?)
      (finally
        (runtime/release-gui!)))))
