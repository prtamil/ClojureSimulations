;; THIS FILE IS A THINKING LOG
;; Order does not matter
;; Safe to delete anything
(ns repl
  (:require
   [sim.model :as model]
   [sim.ascii :as ascii]
   [driver :as driver]))

; test 

(model/initial-world)
(ascii/render (model/update-world (model/initial-world)))

;start gui



;; after experiment we can update driver which is driver code so others can execute
;;experiment 
(defonce world (atom (model/initial-world)))


(defn step!
  ([] (step! 1 50))
  ([n] (step! n 50))
  ([n sleep-ms]
   (doseq [_ (range n)]
     (swap! world model/update-world)
     (ascii/render @world)
     (Thread/sleep sleep-ms))))




(step! 30 2000)