;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns driver
  (:gen-class)
  (:require
   [bonsai.app.controller :as controller]
   [bonsai.app.runtime :as runtime]
   [bonsai.model.core :as model]
   [bonsai.render.ascii :as ascii]
   [bonsai.render.lanterna :as lanterna]))

(def terminal-started? runtime/terminal-running?)

(defn world [] @runtime/world)

(defn preview
  ([] (preview {}))
  ([options] (println (ascii/render-string @runtime/world options))))

(defn command! [command]
  (swap! runtime/world controller/apply-command command))

(defn step!
  ([] (swap! runtime/world model/advance-world))
  ([tick-count]
   (swap! runtime/world model/advance-world-steps tick-count
          (/ 1.0 (get-in @runtime/world [:clock :simulation-fps])))))

(defn reset-world! []
  (clojure.core/reset! runtime/world (model/initial-world)))

(defn start-gui! []
  (require 'bonsai.render.quil)
  ((resolve 'bonsai.render.quil/run-gui)))

(defn start-terminal! []
  (lanterna/start!))

(defn -main [& _]
  (preview)
  (println)
  (println "Interactive terminal: clojure -M:terminal")
  (println "Interactive GUI: clojure -M:gui")
  (shutdown-agents))
