;; Disposable REPL laboratory. Promote stable experiments into model or driver.
(ns repl
  (:require
   [driver :as driver]
   [fireworks.model.config :as config]
   [fireworks.model.core :as model]
   [fireworks.render.ascii :as ascii]))

(comment
  (require '[driver :as driver]
           '[fireworks.model.config :as config]
           '[fireworks.model.core :as model]
           '[fireworks.render.ascii :as ascii]
           :reload)

  ;; Shared runtime workflow.
  (driver/reset-world!)
  (driver/step! 30)
  (driver/preview)
  (driver/command! :more-rockets)
  (driver/command! :next-theme)

  ;; Pure, deterministic workflow.
  (def world-0 (model/initial-world 42))
  (def world-1 (model/update-world world-0))
  (= world-0 (model/initial-world 42))
  (ascii/render world-1)

  ;; Change the logical display size without touching a renderer.
  (def compact-config
    (-> config/default-config
        (assoc-in [:grid :width] 48)
        (assoc-in [:grid :height] 20)))
  (def compact-world (model/initial-world compact-config 42))
  (ascii/render (model/update-world-steps compact-world 45 (/ 1.0 30.0)))

  (driver/start-gui!)
  (driver/start-terminal!)
  )
