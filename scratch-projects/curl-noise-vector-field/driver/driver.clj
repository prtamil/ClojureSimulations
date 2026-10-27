;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
;; Stable helpers: reset, step, controls, and GUI launch.
(ns driver
  (:require
   [sim.ascii :as ascii]
   [sim.controller :as controller]
   [sim.model :as model]
   [sim.quil :as quil]
   [sim.runtime :as runtime]))

;; Compatibility aliases for REPL callers; Quil reads the same atom.
(def world runtime/world)
(def gui-started? runtime/gui-running?)

(defn reset-world! []
  (swap! world model/reset-world))

(defn step!
  ([] (step! 1))
  ([n]
   (swap! world
          #(model/update-world-steps % n (/ 1.0 (:sim-fps %))))
   (ascii/render @world)
   @world))

(defn next-pattern! []
  (swap! world controller/apply-command :next-pattern))

(defn previous-pattern! []
  (swap! world controller/apply-command :previous-pattern))

(defn next-theme! []
  (swap! world controller/apply-command :next-theme))

(defn previous-theme! []
  (swap! world controller/apply-command :previous-theme))

(defn pause! []
  (swap! world assoc :paused? true))

(defn resume! []
  (swap! world assoc :paused? false))

(defn start-gui! []
  (quil/run-gui))

(defn -main [& _args]
  (println "Headless curl-noise preview (launch the Quil view with driver/start-gui!)")
  (step! 8))

