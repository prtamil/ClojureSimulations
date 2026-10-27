;; REPL LEVEL GUIDE
;; ----------------
;; This is a disposable laboratory. Work in this order:
;; RESET -> STEP -> INSPECT -> EXPERIMENT -> PROMOTE -> GUI.
;; Keep proven state transitions in sim.model, settings in sim.config, stable
;; commands in driver, and visual code in sim.ascii or sim.quil.
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
  ;; Driver commands update the same atom used by an open Quil window.
  (driver/reset-world!)
  (select-keys @driver/world
               [:tick :field-time :paused? :speed :drift-mult
                :sim-fps :pattern :theme-index])

  ;; step! advances, prints an ASCII view, and returns the new world.
  (driver/step!)
  (driver/step! 20)
  (driver/next-pattern!)
  (driver/step! 1)
  (driver/previous-pattern!)
  (driver/next-theme!)
  (driver/previous-theme!)
  (driver/pause!)
  (driver/step! 10) ; paused: model time does not advance
  (driver/resume!)

  ;; Inspect a focused slice instead of printing the complete large world.
  (take 3 (:particles @driver/world))
  (apply max (get-in @driver/world [:buffers :glow]))
  (take 5 (get-in @driver/world [:buffers :vectors]))

  ;; -------------------------------------------------------------------------
  ;; 2. PURE MODEL WORKFLOW
  ;; -------------------------------------------------------------------------
  ;; Keep before/after values. No runtime atom is needed for model questions.
  (def world-0 (model/initial-world 42))
  (def world-1 (model/update-world world-0))
  (def world-60
    (model/update-world-steps world-0 60 (/ 1.0 60.0)))

  (= world-0 (model/initial-world 42)) ; same seed => same world
  (= world-0 world-1)                  ; expected: false
  (select-keys world-60 [:tick :field-time :supernova])
  (ascii/render world-60)

  ;; Iterate is useful when every intermediate world matters.
  (def timeline (take 11 (iterate model/update-world world-0)))
  (mapv :tick timeline)
  (mapv #(-> % :particles first (select-keys [:x :y :age])) timeline)

  ;; -------------------------------------------------------------------------
  ;; 3. EXPLORE ALL VISUALIZATION PATTERNS
  ;; -------------------------------------------------------------------------
  (def pattern-worlds
    (into {}
          (map (fn [pattern]
                 [pattern
                  (-> world-0
                      (assoc :pattern pattern)
                      model/update-world)]))
          config/pattern-order))
  (doseq [pattern config/pattern-order]
    (println "\n" (config/pattern-name pattern))
    (ascii/render (get pattern-worlds pattern)))

  ;; -------------------------------------------------------------------------
  ;; 4. LOCAL ATOM WORKFLOW
  ;; -------------------------------------------------------------------------
  ;; Use a scratch atom to test stateful calling patterns without changing
  ;; driver/world or an open GUI.
  (def scratch-world (atom (model/initial-world 42)))
  (swap! scratch-world model/update-world)
  (swap! scratch-world model/update-world-steps 20 (/ 1.0 60.0))
  (ascii/render @scratch-world)

  ;; -------------------------------------------------------------------------
  ;; 5. INSPECT NOISE AND CURL DIRECTLY
  ;; -------------------------------------------------------------------------
  (noise/potential-at (:noise-permutation world-0) 1.2 3.4 0.0)
  (noise/warped-potential-at (:noise-permutation world-0) 1.2 3.4 0.0)
  (noise/curl-at (:noise-permutation world-0) 1.2 3.4 0.0 false)
  (noise/curl-at (:noise-permutation world-0) 1.2 3.4 0.0 true)

  ;; -------------------------------------------------------------------------
  ;; 6. CONFIGURATION EXPERIMENTS
  ;; -------------------------------------------------------------------------
  (def small-config
    (-> config/default-config
        (assoc-in [:grid :width] 48)
        (assoc-in [:grid :height] 28)
        (assoc-in [:particles :count] 64)))
  (def small-world (model/initial-world small-config 42))
  (ascii/render small-world)

  ;; Invalid settings fail at world construction with the config path.
  (model/initial-world (assoc-in config/default-config [:grid :width] 0) 42)

  ;; -------------------------------------------------------------------------
  ;; 7. RUN MODEL TESTS FROM THE REPL PROMPT
  ;; -------------------------------------------------------------------------
  ;; The :dev alias includes test/ on the classpath.
  (require '[clojure.test :as test]
           '[sim.model-test]
           :reload)
  (test/run-tests 'sim.model-test)

  ;; -------------------------------------------------------------------------
  ;; 8. PROMOTE, THEN VISUALIZE
  ;; -------------------------------------------------------------------------
  ;; When an experiment works:
  ;; - move calculations/state transitions to sim.model;
  ;; - move reusable settings to sim.config;
  ;; - add an ASCII representation and tests;
  ;; - expose stable interactive commands through driver;
  ;; - add or change Quil drawing last.
  (driver/start-gui!)
  )

