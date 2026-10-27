;; REPL LEVEL GUIDE
;; ----------------
;; This is a disposable laboratory, not application code. Work in this order:
;; RESET -> STEP -> INSPECT -> EXPERIMENT -> PROMOTE -> GUI.
;;
;; Keep successful simulation rules in sim.model, stable commands in driver,
;; and visual code in sim.ascii or sim.quil. It is safe to rewrite this file.
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

  ;; Evaluate one form at a time after the require. Nothing inside this comment
  ;; runs automatically when the repl namespace is loaded.

  ;; -------------------------------------------------------------------------
  ;; 1. SHARED RUNTIME WORKFLOW
  ;; -------------------------------------------------------------------------
  ;; Driver functions operate on the same atom used by the Quil window.
  (driver/reset-world!)
  @driver/world

  ;; step! prints an ASCII snapshot and returns the new immutable world.
  (driver/step!)
  (driver/step! 20)

  ;; Inspect only the part relevant to the current question.
  (select-keys @driver/world
               [:tick :elapsed-seconds :paused? :sim-fps :theme-index])
  (mapv #(select-keys % [:id :x :y]) (:particles @driver/world))

  ;; Runtime controls are useful while an ASCII or Quil session is active.
  (driver/pause!)
  (driver/step! 10) ; paused: tick and particles remain unchanged
  (driver/resume!)
  (driver/next-theme!)
  (driver/previous-theme!)

  ;; -------------------------------------------------------------------------
  ;; 2. PURE MODEL WORKFLOW (preferred for understanding and testing)
  ;; -------------------------------------------------------------------------
  ;; No atom is needed. Keep before/after values so they can be compared.
  (def world-0 (model/initial-world 42))
  (def world-1 (model/update-world world-0))
  (def world-60
    (model/update-world-steps world-0 60 (/ 1.0 60.0)))

  (ascii/render world-0)
  (ascii/render world-60)
  (= world-0 (model/initial-world 42)) ; same seed => same world
  (= world-0 world-1)                  ; expected: false

  ;; Use iterate when every intermediate world matters.
  (def timeline (take 11 (iterate model/update-world world-0)))
  (mapv :tick timeline)
  (mapv #(-> % :particles first (select-keys [:x :y])) timeline)

  ;; Use update-world-steps when only the final state of a fixed-step batch is
  ;; needed. This is the same boundary used by the runtime clock. Compare
  ;; floating-point results with a tolerance rather than exact equality.
  (def batched (model/update-world-steps world-0 10 (/ 1.0 60.0)))
  (every? #(< % 1.0e-12)
          (map (fn [from-batch from-timeline]
                 (Math/hypot (- (:x from-batch) (:x from-timeline))
                             (- (:y from-batch) (:y from-timeline))))
               (:particles batched)
               (:particles (last timeline))))

  ;; -------------------------------------------------------------------------
  ;; 3. LOCAL ATOM WORKFLOW
  ;; -------------------------------------------------------------------------
  ;; Use a private scratch atom when testing stateful calling patterns without
  ;; changing driver/world or an open GUI.
  (def scratch-world (atom (model/initial-world 42)))
  (swap! scratch-world model/update-world)
  (swap! scratch-world model/update-world-steps 20 (/ 1.0 60.0))
  (ascii/render @scratch-world)

  ;; -------------------------------------------------------------------------
  ;; 4. CONFIGURATION EXPERIMENTS
  ;; -------------------------------------------------------------------------
  ;; Start from default-config, override a small number of values, then let
  ;; initial-world validate the complete result at the boundary.
  (def small-config
    (-> config/default-config
        (assoc-in [:grid :width] 24)
        (assoc-in [:grid :height] 12)
        (assoc-in [:particles :initial]
                  [{:id 1 :x 2.0 :y 3.0 :vx 4.0 :vy 2.0}])))

  (def small-world (model/initial-world small-config 42))
  (ascii/render small-world)

  ;; Invalid experiments fail early and report the configuration path.
  (config/validate! (assoc-in config/default-config [:grid :width] 0))

  ;; -------------------------------------------------------------------------
  ;; 5. RANDOMNESS AND NOISE EXPERIMENTS
  ;; -------------------------------------------------------------------------
  ;; Random/noise state comes from the seed stored in the world. Never call a
  ;; process-global RNG from model updates if reproducibility matters.
  (noise/sample-2d (:noise-permutation world-0) 12.0 8.0)
  (noise/sample-2d (:noise-permutation world-0) 13.0 8.0)

  ;; Same seed and coordinates produce the same sample.
  (= (noise/sample-2d (:noise-permutation (model/initial-world 42)) 12.0 8.0)
     (noise/sample-2d (:noise-permutation (model/initial-world 42)) 12.0 8.0))

  ;; -------------------------------------------------------------------------
  ;; 6. PROMOTE, THEN VISUALIZE
  ;; -------------------------------------------------------------------------
  ;; When an experiment works:
  ;; - move state transitions/calculations to sim.model;
  ;; - move reusable settings to sim.config;
  ;; - add an ASCII representation and tests;
  ;; - expose stable interactive operations through driver;
  ;; - add Quil drawing last.

  ;; Launch the GUI only after the model and ASCII view answer the question.
  (driver/start-gui!)

  ;; -------------------------------------------------------------------------
  ;; 7. RUN MODEL TESTS FROM THE REPL PROMPT
  ;; -------------------------------------------------------------------------
  ;; The :dev alias includes test/ on the classpath.
  (require '[clojure.test :as test]
           '[sim.model-test]
           :reload)
  (test/run-tests 'sim.model-test)
  )
