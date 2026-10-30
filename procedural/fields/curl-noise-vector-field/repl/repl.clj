;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns repl
  "A small REPL sketchbook for the immutable curl-noise model."
  (:require
   [driver :as driver]
   [sim.model.config :as config]
   [sim.model.core :as model]
   [sim.model.field :as field]
   [sim.render.ascii :as ascii]
   [sim.render.projection :as projection]))

(comment
  ;; Seeded worlds make experiments repeatable.
  (def before (model/initial-world 42))
  (def after
    (model/advance-world-steps before 60 (/ 1.0 60.0)))

  (select-keys after [:clock :view :particle-speed :drift-multiplier])
  (model/field-time after)
  (take 3 (:particles after))
  (take 3 (:trail after))
  (take 3 (:samples (projection/project-world after)))
  (ascii/render after)

  ;; View changes are pure and immediately calculate their field samples.
  (def vector-world (model/cycle-view after 1))
  (def potential-world (model/cycle-view vector-world 1))
  (map (juxt :position :velocity)
       (take 5 (vals (get-in vector-world [:view :samples]))))
  (ascii/render vector-world)
  (ascii/render potential-world)

  ;; Field queries use simulation-grid coordinates.
  (field/potential (:config before) (:field before) [24.0 12.0] false)
  (field/direction (:config before) (:field before) [24.0 12.0] false)
  (field/direction (:config before) (:field before) [24.0 12.0] true)

  ;; Configurations are values too.
  (def small-config
    (-> config/default-config
        (assoc :grid {:width 48 :height 28})
        (assoc-in [:particles :count] 64)))
  (def small-world (model/initial-world small-config 42))
  (ascii/render small-world)

  ;; Driver helpers update the atom shared by interactive renderers.
  (driver/reset-world!)
  (driver/step! 20)
  (driver/next-pattern!)
  (driver/next-theme!)
  (driver/start-terminal!)
  (driver/start-gui!)
  )
