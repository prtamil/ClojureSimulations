;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.app.controller
  (:require
   [leaf-fall.model.core :as model]))

(def ^:private key-bindings
  {\q :quit
   \Q :quit
   :esc :quit
   \space :skip
   \r :new-tree
   \R :new-tree
   \] :raise-sim-fps
   \[ :lower-sim-fps})

(defn- normalized-key [{:keys [key raw-key]}]
  (cond
    (char? raw-key) raw-key
    (= :space key) \space
    (and (keyword? key) (= 1 (count (name key)))) (first (name key))
    :else key))

(defn key-event->command [{:keys [key-code] :as event}]
  (if (= 27 key-code)
    :quit
    (get key-bindings (normalized-key event))))

(defn apply-command [world command]
  (let [fps-step (get-in world [:config :clock :simulation-fps :step])]
    (case command
      :skip (model/skip-phase world)
      :new-tree (model/new-tree world)
      :raise-sim-fps (model/change-simulation-fps world fps-step)
      :lower-sim-fps (model/change-simulation-fps world (- fps-step))
      world)))
