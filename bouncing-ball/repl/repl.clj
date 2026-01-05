;; THIS FILE IS A THINKING LOG
;; Order does not matter
;; Safe to delete anything
(ns repl
  (:require
   [sim.model :as model]
   [sim.ascii :as ascii]))

; test 
(def world (model/initial-world))

(def world2 (iterate model/update-world world))

(doseq [w (take 30 world2)]
  (ascii/render w)
  (Thread/sleep 100))

(comment
  (doseq [w (take 20 world2)]
    (sim.ascii/render w)
    (Thread/sleep 100)))




