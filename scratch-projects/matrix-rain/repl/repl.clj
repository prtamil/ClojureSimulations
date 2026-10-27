;; REPL LEVEL GUIDE
;; ----------------
;; RESET -> STEP -> INSPECT -> EXPERIMENT -> PROMOTE -> GUI.
(ns repl
  (:require [driver :as driver]
            [sim.ascii :as ascii]
            [sim.config :as config]
            [sim.model :as model]
            [sim.noise :as noise]))

(comment
  ;; REPL PROMPT START HERE
  (require '[driver :as driver]
           '[sim.ascii :as ascii]
           '[sim.config :as config]
           '[sim.model :as model]
           '[sim.noise :as noise]
           :reload)

  ;; 1. Shared runtime workflow.
  (driver/reset-world!)
  (driver/step!)
  (driver/step! 30)
  (select-keys @driver/world
               [:tick :elapsed-seconds :paused? :speed-mult
                :sim-fps :theme-index])
  (take 3 (:drops @driver/world))

  (driver/pause!)
  (driver/step! 10)
  (driver/resume!)
  (driver/faster!)
  (driver/slower!)
  (driver/next-theme!)
  (driver/play-ascii! 20 100)

  ;; 2. Pure deterministic model workflow.
  (def world-0 (model/initial-world 42))
  (def world-60
    (model/update-world-steps world-0 60 (/ 1.0 60.0)))
  (= world-0 (model/initial-world 42))
  (= (ascii/render-string world-60) (ascii/render-string world-60))
  (ascii/render world-60)

  ;; 3. Keep a timeline when studying movement, mutation, or respawn.
  (def timeline (take 121 (iterate model/update-world world-0)))
  (mapv #(select-keys % [:id :column :head-y :speed :length])
        (:drops (last timeline)))

  ;; 4. Local stateful experiment without changing driver/world.
  (def scratch-world (atom (model/initial-world 42)))
  (swap! scratch-world model/update-world-steps 20 (/ 1.0 60.0))
  (ascii/render @scratch-world)

  ;; 5. Configuration experiment.
  (def small-config
    (-> config/default-config
        (assoc-in [:grid :width] 24)
        (assoc-in [:grid :height] 12)
        (assoc-in [:rain :drop-count] 8)
        (assoc-in [:rain :length :max] 10)))
  (def small-world (model/initial-world small-config 42))
  (ascii/render small-world)

  ;; Seeded noise is available for derived wind, density, or glyph effects.
  (noise/sample-2d (:noise-permutation world-0) 12.0 8.0)

  ;; 6. Run model tests from a development REPL.
  (require '[clojure.test :as test]
           '[sim.model-test]
           :reload)
  (test/run-tests 'sim.model-test)

  ;; 7. Promote proven rules/config/ASCII/tests, then add graphics last.
  (driver/start-gui!)
  )
