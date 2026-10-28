;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.particle
  "Pure motion rules for the framework's example particles."
  (:require [sim.model.config :as config]))

(defn- wrap [value limit]
  (mod (double value) (double limit)))

(defn advance
  "Move one particle by elapsed seconds and wrap it at the grid edges."
  [simulation-config seconds {:keys [x y vx vy] :as particle}]
  (assoc particle
         :x (wrap (+ x (* vx seconds)) (config/grid-width simulation-config))
         :y (wrap (+ y (* vy seconds)) (config/grid-height simulation-config))))

(defn advance-all [simulation-config particles seconds]
  (mapv #(advance simulation-config seconds %) particles))
