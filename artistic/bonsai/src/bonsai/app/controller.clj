;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.app.controller
  (:require
   [bonsai.model.core :as model]))

(def ^:private key-bindings
  {\q :quit
   \Q :quit
   :esc :quit
   \space :toggle-pause
   \r :reseed
   \R :reseed
   \n :next-style
   \N :previous-style
   \t :next-theme
   \T :previous-theme
   \] :raise-sim-fps
   \[ :lower-sim-fps
   \+ :stronger-wind
   \= :stronger-wind
   \- :weaker-wind
   \_ :weaker-wind
   \g :replay-growth
   \G :replay-growth})

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

(defn apply-command
  "Apply a pure application command. :quit is owned by the GUI adapter."
  [world command]
  (let [fps-step (get-in world [:config :clock :simulation-fps :step])]
    (case command
      :toggle-pause (model/toggle-paused world)
      :reseed (model/reseed world)
      :next-style (model/cycle-style world 1)
      :previous-style (model/cycle-style world -1)
      :next-theme (model/cycle-theme world 1)
      :previous-theme (model/cycle-theme world -1)
      :raise-sim-fps (model/change-simulation-fps world fps-step)
      :lower-sim-fps (model/change-simulation-fps world (- fps-step))
      :stronger-wind (model/adjust-wind world 1)
      :weaker-wind (model/adjust-wind world -1)
      :replay-growth (model/replay-growth world)
      world)))
