;; REPL LEVEL GUIDE
;; ----------------
;; RESET -> STEP -> INSPECT -> EXPERIMENT -> PROMOTE -> GUI.
(ns repl
  (:require
   [driver :as driver]
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
  (driver/step! 20)
  (select-keys @driver/world
               [:tick :elapsed-seconds :paused? :speed-mult
                :sim-fps :theme-index])
  (take 3 (:balls @driver/world))

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
  (ascii/render world-0)
  (ascii/render world-60)

  ;; 3. Inspect a direct head-on collision.
  (def collision
    (model/resolve-ball-collision
     1.0
     {:id 0 :x 10.0 :y 10.0 :vx 1.0 :vy 0.0
      :radius 0.5 :mass 0.25 :color 0}
     {:id 1 :x 11.0 :y 10.0 :vx -1.0 :vy 0.0
      :radius 0.5 :mass 0.25 :color 1}))
  collision

  ;; 4. Keep a timeline when diagnosing a collision sequence.
  (def timeline (take 121 (iterate model/update-world world-0)))
  (mapv #(select-keys % [:id :x :y :vx :vy])
        (:balls (last timeline)))

  ;; 5. Use a local atom without changing the shared GUI world.
  (def scratch-world (atom (model/initial-world 42)))
  (swap! scratch-world model/update-world-steps 20 (/ 1.0 60.0))
  (ascii/render @scratch-world)

  ;; 6. Configuration experiment.
  (def small-config
    (-> config/default-config
        (assoc-in [:grid :width] 24)
        (assoc-in [:grid :height] 12)
        (assoc-in [:balls :count] 6)
        (assoc-in [:balls :radius :max] 0.6)))
  (def small-world (model/initial-world small-config 42))
  (ascii/render small-world)

  ;; The seeded noise facility is ready for derived motion or environments.
  (noise/sample-2d (:noise-permutation world-0) 12.0 8.0)

  ;; 7. Run model tests from a development REPL.
  (require '[clojure.test :as test]
           '[sim.model-test]
           :reload)
  (test/run-tests 'sim.model-test)

  ;; 8. Promote proven rules/config/ASCII/tests, then launch graphics last.
  (driver/start-gui!)
  )
