;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns driver
  (:gen-class)
  (:require
   [fireworks.app.controller :as controller]
   [fireworks.app.runtime :as runtime]
   [fireworks.model.core :as model]
   [fireworks.render.ascii :as ascii]
   [fireworks.render.lanterna :as lanterna]))

(def terminal-started? runtime/terminal-running?)

(defn world [] @runtime/world)

(defn preview []
  (println (ascii/render-string @runtime/world)))

(defn command! [command]
  (swap! runtime/world controller/apply-command command))

(defn step!
  ([] (swap! runtime/world model/update-world))
  ([tick-count]
   (swap! runtime/world model/update-world-steps tick-count
          (/ 1.0 (:sim-fps @runtime/world)))))

(defn reset-world! []
  (reset! runtime/world (model/initial-world)))

(defn start-gui! []
  (require 'fireworks.render.quil)
  ((resolve 'fireworks.render.quil/run-gui)))

(defn start-terminal! []
  (lanterna/start!))

(defn -main [& _]
  ;; Advance far enough for a headless invocation to show both trails and a
  ;; burst instead of the intentionally staggered all-idle first frame.
  (step! 45)
  (preview)
  (println)
  (println "Interactive terminal: clojure -M:terminal")
  (println "Interactive GUI: clojure -M:gui")
  (shutdown-agents))
