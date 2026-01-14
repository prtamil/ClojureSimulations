;; stable helpers (start/stop, reset)
(ns driver
  (:require
   [sim.model :as model]
   [sim.ascii :as ascii]
   [sim.quil :as quil]))

(defonce world (atom (model/initial-world)))
(defonce gui-started? (atom false))

;; -------------------------
;; WORLD CONTROL
;; -------------------------

(defn reset-world!
  []
  (reset! world (model/initial-world))
  (ascii/render @world))

(defn step!
  "Advance the world by n ticks (default 1) and render ASCII output."
  ([] (step! 1))
  ([n]
   (swap! world
          (fn [w]
            (nth (iterate model/update-world w) n)))
   (ascii/render @world)))

;; -------------------------
;; GUI (optional)
;; -------------------------

(defn start-gui!
  []
  (when-not @gui-started?
    (quil/run-gui)
    (reset! gui-started? true)))

;; -------------------------
;; ENTRY POINT
;; -------------------------

(defn -main
  [& _args]
  ;; ASCII mode by default
  (ascii/render @world))
