;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.app.controller
  (:require
   [fireworks.model.core :as model]))

(def ^:private key-bindings
  {\q :quit
   \Q :quit
   :esc :quit
   \] :raise-sim-fps
   \[ :lower-sim-fps
   \+ :more-rockets
   \= :more-rockets
   \- :fewer-rockets
   \_ :fewer-rockets
   \t :next-theme
   \T :previous-theme
   \space :toggle-pause
   \r :restart-show
   \R :restart-show})

(defn- normalized-key [{:keys [key raw-key]}]
  (cond
    (char? raw-key) raw-key
    (= :space key) \space
    (and (keyword? key) (= 1 (count (name key)))) (first (name key))
    :else key))

(defn key-event->command
  "Translate a Quil event map into a renderer-independent command."
  [{:keys [key-code] :as event}]
  (if (= 27 key-code)
    :quit
    (get key-bindings (normalized-key event))))

(defn apply-command [world command]
  (let [fps-step (get-in world [:config :clock :simulation-fps :step])
        rocket-step (get-in world [:config :rockets :count :step])]
    (case command
      :toggle-pause (model/toggle-paused world)
      :restart-show (model/restart-show world)
      :next-theme (model/cycle-theme world 1)
      :previous-theme (model/cycle-theme world -1)
      :raise-sim-fps (model/change-simulation-speed world fps-step)
      :lower-sim-fps (model/change-simulation-speed world (- fps-step))
      :more-rockets (model/change-firework-count world rocket-step)
      :fewer-rockets (model/change-firework-count world (- rocket-step))
      world)))
