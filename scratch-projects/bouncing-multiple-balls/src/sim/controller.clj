;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.controller
  (:require
   [sim.model :as model]
   [sim.theme :as theme]))

(def ^:private key-bindings
  {\q :quit \Q :quit :esc :quit
   \space :toggle-pause
   \r :reset \R :reset
   \t :next-theme \T :previous-theme
   \+ :faster \= :faster \- :slower
   \] :raise-sim-fps \[ :lower-sim-fps})

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
      :toggle-pause (model/toggle-paused world)
      :reset (model/reset-world world)
      :next-theme (theme/cycle-theme world 1)
      :previous-theme (theme/cycle-theme world -1)
      :faster (model/adjust-speed world 1)
      :slower (model/adjust-speed world -1)
      :raise-sim-fps (model/adjust-sim-fps world fps-step)
      :lower-sim-fps (model/adjust-sim-fps world (- fps-step))
      world)))
