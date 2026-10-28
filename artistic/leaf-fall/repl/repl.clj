;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns repl
  (:require
   [driver :as driver]
   [leaf-fall.model.core :as model]
   [leaf-fall.render.ascii :as ascii]))

(comment
  (driver/preview {:columns 80 :rows 32})
  (driver/command! :skip)
  (driver/step! 120)
  (driver/command! :new-tree)
  (ascii/render (driver/world))
  (driver/start-gui!)

  (def deterministic-tree (model/initial-world 42))
  (= deterministic-tree (model/initial-world 42)))
