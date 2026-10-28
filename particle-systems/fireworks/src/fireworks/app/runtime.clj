;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.app.runtime
  (:require
   [fireworks.model.core :as model]))

(defonce world (atom (model/initial-world)))
(defonce gui-running? (atom false))
(defonce terminal-running? (atom false))

(defn claim-gui! []
  (compare-and-set! gui-running? false true))

(defn release-gui! []
  (reset! gui-running? false))

(defn claim-terminal! []
  (compare-and-set! terminal-running? false true))

(defn release-terminal! []
  (reset! terminal-running? false))
