;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
;; Stable helpers: reset, step, controls, and GUI launch.
(ns driver
  (:gen-class)
  (:require
   [sim.render.ascii :as ascii]
   [sim.app.controller :as controller]
   [sim.model.core :as model]
   [sim.render.lanterna :as lanterna]
   [sim.app.runtime :as runtime]))

;; Compatibility aliases for REPL callers; Quil reads the same atom.
(def world runtime/world)
(def gui-started? runtime/gui-running?)
(def terminal-started? runtime/terminal-running?)

(defn reset-world! []
  (swap! world model/reset-world))

(defn step!
  ([] (step! 1))
  ([n]
   (swap! world
          #(model/advance-world-steps
            % n (/ 1.0 (get-in % [:clock :simulation-fps]))))
   (ascii/render @world)
   @world))

(defn pause! []
  (swap! world assoc-in [:clock :paused?] true))

(defn resume! []
  (swap! world assoc-in [:clock :paused?] false))

(defn next-theme! []
  (swap! world controller/apply-command :next-theme))

(defn previous-theme! []
  (swap! world controller/apply-command :previous-theme))

(defn raise-sim-fps! []
  (swap! world controller/apply-command :raise-sim-fps))

(defn lower-sim-fps! []
  (swap! world controller/apply-command :lower-sim-fps))

(defn start-gui! []
  (require 'sim.render.quil)
  ((resolve 'sim.render.quil/run-gui)))

(defn start-terminal! []
  (lanterna/start!))

(defn -main [& _args]
  (println "Headless basic simulation preview (use :terminal or driver/start-gui!)")
  (step! 8))
