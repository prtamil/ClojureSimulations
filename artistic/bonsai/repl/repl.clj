;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns repl
  (:require
   [bonsai.model.core :as model]
   [bonsai.render.ascii :as ascii]
   [driver :as driver]))

(comment
  (driver/preview)
  (driver/command! :next-style)
  (driver/command! :next-theme)
  (driver/command! :stronger-wind)
  (driver/command! :replay-growth)
  (driver/step! 84)
  (ascii/render (driver/world))
  (driver/start-gui!)

  (def deterministic-tree (model/initial-world 42))
  (= deterministic-tree (model/initial-world 42))
  )
