;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns repl
  (:require
   [driver :as driver]
   [sim.model.ball :as ball]
   [sim.model.core :as model]
   [sim.render.ascii :as ascii]))

(comment
  (def before (model/initial-world 42))
  (def after (model/update-world-steps before 60 (/ 1.0 60.0)))
  (:ball before)
  (:ball after)
  (ascii/render after)

  (ball/advance-axis 39.0 12.0 0.25 0.45 39.55)
  (model/project-world after 0.01)

  (driver/reset-world!)
  (driver/step! 20)
  (driver/start-terminal!)
  (driver/start-gui!)
  )
