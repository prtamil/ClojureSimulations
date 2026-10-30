;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns repl
  (:require
   [driver :as driver]
   [sim.model.ball :as ball]
   [sim.model.collision :as collision]
   [sim.model.core :as model]
   [sim.render.ascii :as ascii]))

(comment
  (def before (model/initial-world 42))
  (def after (model/advance-world-steps before 120 (/ 1.0 60.0)))
  (take 3 (:balls after))
  (ascii/render after)

  (ball/overlap? (first (:balls before)) (second (:balls before)))
  (apply collision/resolve-pair 1.0 (take 2 (:balls before)))

  (driver/reset-world!)
  (driver/step! 20)
  (driver/start-terminal!)
  (driver/start-gui!)
  )
