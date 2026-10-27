;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.runtime
  (:require [sim.model :as model]))

(defonce world (atom (model/initial-world)))
(defonce gui-running? (atom false))

(defn claim-gui! [] (compare-and-set! gui-running? false true))
(defn release-gui! [] (reset! gui-running? false))
