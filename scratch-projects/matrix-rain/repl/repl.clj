;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns repl
  (:require
   [driver :as driver]
   [sim.model.core :as model]
   [sim.model.drop :as drop]
   [sim.render.ascii :as ascii]))

(comment
  (def before (model/initial-world 42))
  (def after (model/advance-world-steps before 60 (/ 1.0 60.0)))
  (take 3 (:drops after))
  (ascii/render after)

  (drop/spawn (:config before) 7 0)

  (driver/reset-world!)
  (driver/step! 20)
  (driver/start-terminal!)
  (driver/start-gui!)
  )
