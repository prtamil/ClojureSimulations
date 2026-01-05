;; stable helpers (start/stop, reset)
(ns driver
  (:require
   [sim.model :as model]
   [sim.ascii :as ascii]
   [sim.quil :as quil]))

(defonce world (atom (model/initial-world)))
(defonce gui-started? (atom false))

(defn reset-world!
  []
  (reset! world (model/initial-world)))

(defn step!
  ([] (step! 1))
  ([n]
   (swap! world
          (fn [w]
            (nth (iterate model/update-world w) n)))
   (ascii/render @world)))

(defn start-gui!
  []
  (when-not @gui-started?
    (quil/run-gui)
    (reset! gui-started? true)))


(defn -main
  [& _args]
  ;; choose what "running the app" means
  ;; ASCII mode first, GUI optional
  (ascii/render @world))