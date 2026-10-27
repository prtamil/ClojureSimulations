;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.runtime
  (:require
   [sim.model :as model]))

;; The driver and graphical adapter intentionally share these two process-level
;; references. The simulation value inside world remains immutable.
(defonce world (atom (model/initial-world)))
(defonce gui-running? (atom false))

(defn claim-gui! []
  (compare-and-set! gui-running? false true))

(defn release-gui! []
  (reset! gui-running? false))

