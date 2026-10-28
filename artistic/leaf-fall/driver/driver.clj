;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns driver
  (:gen-class)
  (:require
   [leaf-fall.app.controller :as controller]
   [leaf-fall.app.runtime :as runtime]
   [leaf-fall.model.core :as model]
   [leaf-fall.render.ascii :as ascii]
   [leaf-fall.render.lanterna :as lanterna]))

(def terminal-started? runtime/terminal-running?)

(defn world [] @runtime/world)

(defn preview
  ([] (preview {}))
  ([options]
   (println (ascii/render-string @runtime/world options))))

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
  (require 'leaf-fall.render.quil)
  ((resolve 'leaf-fall.render.quil/run-gui)))

(defn start-terminal! []
  (lanterna/start!))

(defn -main [& _]
  (preview {:columns 80 :rows 32})
  (println)
  (println "Interactive terminal: clojure -M:terminal")
  (println "Interactive GUI: clojure -M:gui")
  (shutdown-agents))
