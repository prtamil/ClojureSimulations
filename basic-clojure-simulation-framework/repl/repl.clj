;; THIS FILE IS A THINKING LOG
;; Order does not matter
;; Safe to delete anything
(ns repl
  (:require
   [sim.model :as model]
   [sim.ascii :as ascii]
   [sim.quil :as quil]
   [driver :as driver]))

; test 
(driver/reset-world!)
(driver/step! 20)
(driver/step! 1)
(driver/step! 2)
; test model
(model/initial-world)
(ascii/render (model/update-world (model/initial-world)))

;start gui
(driver/start-gui!)


;; after experiment we can update driver which is driver code so others can execute
;;experiment 
(defonce world (atom (model/initial-world)))

(:particles world)

(defn step!
  ([] (step! 1))
  ([n]
   (swap! world #(nth (iterate model/update-world %) n))))

(ascii/render (step! 1))



