;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
;; Stable helpers: reset, step, controls, terminal animation, and GUI launch.
(ns driver
  (:gen-class)
  (:require
   [sim.app.controller :as controller]
   [sim.app.runtime :as runtime]
   [sim.model.core :as model]
   [sim.render.ascii :as ascii]
   [sim.render.lanterna :as lanterna]))

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

(defn next-pattern! []
  (swap! world controller/apply-command :next-pattern))

(defn previous-pattern! []
  (swap! world controller/apply-command :previous-pattern))

(defn next-theme! []
  (swap! world controller/apply-command :next-theme))

(defn previous-theme! []
  (swap! world controller/apply-command :previous-theme))

(defn pause! []
  (swap! world assoc-in [:clock :paused?] true))

(defn resume! []
  (swap! world assoc-in [:clock :paused?] false))

(defn start-gui! []
  (require 'sim.render.quil)
  ((resolve 'sim.render.quil/run-gui)))

(defn start-terminal!
  "Run the interactive Lanterna renderer in the current terminal."
  []
  (lanterna/start!))

(defn -main [& _args]
  (println (str "Headless curl-noise preview "
                "(use :terminal or driver/start-gui! for animation)"))
  (step! 8))

