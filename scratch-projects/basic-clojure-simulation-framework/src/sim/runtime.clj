;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.runtime
  (:require
   [sim.model :as model]))

;; The immutable value inside this atom is the single process-level world used
;; by the driver and GUI. Keeping the atom here prevents adapter-local copies.
(defonce world (atom (model/initial-world)))
(defonce gui-running? (atom false))

(defn claim-gui! []
  (compare-and-set! gui-running? false true))

(defn release-gui! []
  (reset! gui-running? false))
