;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns repl
  (:require
   [driver :as driver]
   [sim.model.core :as model]
   [sim.model.particle :as particle]
   [sim.render.ascii :as ascii]))

(comment
  (def before (model/initial-world 42))
  (def after (model/update-world-steps before 60 (/ 1.0 60.0)))
  (select-keys after [:tick :elapsed-seconds :paused? :sim-fps])
  (:particles after)
  (ascii/render after)

  (particle/advance (:config before) 0.5 (first (:particles before)))

  (driver/reset-world!)
  (driver/step! 20)
  (driver/start-terminal!)
  (driver/start-gui!)
  )
