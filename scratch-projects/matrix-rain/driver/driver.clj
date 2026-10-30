;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns driver
  (:gen-class)
  (:require [sim.render.ascii :as ascii]
            [sim.app.controller :as controller]
            [sim.model.core :as model]
            [sim.render.lanterna :as lanterna]
            [sim.app.runtime :as runtime]))

(def world runtime/world)
(def gui-started? runtime/gui-running?)
(def terminal-started? runtime/terminal-running?)

(defn reset-world! [] (swap! world model/reset-world))

(defn step!
  ([] (step! 1))
  ([n]
   (swap! world #(model/advance-world-steps
                  % n (/ 1.0 (get-in % [:clock :simulation-fps]))))
   (ascii/render @world)
   @world))

(defn pause! [] (swap! world assoc-in [:clock :paused?] true))
(defn resume! [] (swap! world assoc-in [:clock :paused?] false))
(defn next-theme! [] (swap! world controller/apply-command :next-theme))
(defn previous-theme! [] (swap! world controller/apply-command :previous-theme))
(defn faster! [] (swap! world controller/apply-command :faster))
(defn slower! [] (swap! world controller/apply-command :slower))
(defn raise-sim-fps! [] (swap! world controller/apply-command :raise-sim-fps))
(defn lower-sim-fps! [] (swap! world controller/apply-command :lower-sim-fps))

(defn play-ascii!
  "Animate a finite number of deterministic ASCII frames."
  ([frames] (play-ascii! frames 100))
  ([frames delay-ms]
   (when-not (and (nat-int? frames) (nat-int? delay-ms))
     (throw (ex-info "Frames and delay must be non-negative integers"
                     {:frames frames :delay-ms delay-ms})))
   (dotimes [_ frames]
     (print (str (char 27) "[2J" (char 27) "[;H"))
     (flush)
     (step!)
     (Thread/sleep delay-ms))
   @world))

(defn start-gui! []
  (require 'sim.render.quil)
  ((resolve 'sim.render.quil/run-gui)))
(defn start-terminal! [] (lanterna/start!))

(defn -main [& _args]
  (println "Headless Matrix Rain preview (use :terminal or driver/start-gui!)")
  (step! 30))
