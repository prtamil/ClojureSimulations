;; REPL LEVEL GUIDE
;; ----------------
;; This is a disposable laboratory. Work in this order:
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
  ;; Copy/evaluate this require first. It creates every alias used below.
  (require '[driver :as driver]
           '[sim.ascii :as ascii]
           '[sim.config :as config]
           '[sim.model :as model]
           '[sim.noise :as noise]
           :reload)

  ;; -------------------------------------------------------------------------
  ;; 1. SHARED RUNTIME WORKFLOW
  ;; -------------------------------------------------------------------------
  (driver/reset-world!)
  @driver/world
  (driver/step!)
  (driver/step! 20)
  (select-keys @driver/world
               [:tick :elapsed-seconds :paused? :speed-mult
                :sim-fps :theme-index])
  (:ball @driver/world)

  (driver/pause!)
  (driver/step! 10) ; paused: tick and ball remain unchanged
  (driver/resume!)
  (driver/faster!)
  (driver/slower!)
  (driver/next-theme!)
  (driver/previous-theme!)

  ;; Optional finite terminal animation. Avoid infinite REPL loops.
  (driver/play-ascii! 20 100)

  ;; -------------------------------------------------------------------------
  ;; 2. PURE MODEL WORKFLOW
  ;; -------------------------------------------------------------------------
  (def world-0 (model/initial-world 42))
  (def world-1 (model/update-world world-0))
  (def world-60
    (model/update-world-steps world-0 60 (/ 1.0 60.0)))

  (= world-0 (model/initial-world 42)) ; deterministic
  (= world-0 world-1)                  ; expected: false
  (ascii/render world-0)
  (ascii/render world-60)

  ;; Keep intermediate states when studying a collision.
  (def timeline (take 121 (iterate model/update-world world-0)))
  (mapv #(-> % :ball (select-keys [:x :y :vx :vy]))
        (take-last 5 timeline))

  ;; -------------------------------------------------------------------------
  ;; 3. FORCE AND INSPECT A WALL COLLISION
  ;; -------------------------------------------------------------------------
  (def edge-world
    (assoc world-0 :ball {:x 39.4 :y 10.0
                          :vx 12.0 :vy 0.0 :radius 0.45}))
  (def bounced-world (model/update-world edge-world 0.1))
  (ascii/render edge-world)
  (ascii/render bounced-world)
  (select-keys (:ball bounced-world) [:x :y :vx :vy])

  ;; -------------------------------------------------------------------------
  ;; 4. LOCAL ATOM WORKFLOW
  ;; -------------------------------------------------------------------------
  (def scratch-world (atom (model/initial-world 42)))
  (swap! scratch-world model/update-world)
  (swap! scratch-world model/update-world-steps 20 (/ 1.0 60.0))
  (ascii/render @scratch-world)

  ;; -------------------------------------------------------------------------
  ;; 5. CONFIGURATION EXPERIMENTS
  ;; -------------------------------------------------------------------------
  (def small-config
    (-> config/default-config
        (assoc-in [:grid :width] 24)
        (assoc-in [:grid :height] 12)
        (assoc-in [:ball :initial :x] 4.0)
        (assoc-in [:ball :initial :y] 3.0)
        (assoc-in [:ball :initial :speed] 8.0)))
  (def small-world (model/initial-world small-config 42))
  (ascii/render small-world)

  ;; Invalid settings fail at construction and identify their path.
  (model/initial-world (assoc-in config/default-config [:ball :radius] 50.0) 42)

  ;; -------------------------------------------------------------------------
  ;; 6. SEEDED NOISE FACILITY
  ;; -------------------------------------------------------------------------
  ;; The ball does not need noise, but derived simulations can use the seeded
  ;; permutation already carried by every world.
  (noise/sample-2d (:noise-permutation world-0) 12.0 8.0)

  ;; -------------------------------------------------------------------------
  ;; 7. RUN MODEL TESTS FROM THE REPL PROMPT
  ;; -------------------------------------------------------------------------
  (require '[clojure.test :as test]
           '[sim.model-test]
           :reload)
  (test/run-tests 'sim.model-test)

  ;; -------------------------------------------------------------------------
  ;; 8. PROMOTE, THEN VISUALIZE
  ;; -------------------------------------------------------------------------
  ;; Move proven rules to model, settings to config, stable operations to
  ;; driver, and graphical drawing to Quil only after ASCII and tests agree.
  (driver/start-gui!)
  )
