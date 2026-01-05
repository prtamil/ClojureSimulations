(ns driver
  (:require
   [sim.model :as model]
   [sim.ascii :as ascii]
   [sim.quil :as quil]))

;; --------------------------
;; STATE
;; --------------------------
(defonce world (atom (model/initial-world)))
(defonce gui-started? (atom false))

;; --------------------------
;; WORLD MANAGEMENT
;; --------------------------
(defn reset-world! []
  (reset! world (model/initial-world)))

(defn step!
  "Advance world by n steps (default 1) and render in ASCII"
  ([] (step! 1))
  ([n]
   ;; update world n steps
   (swap! world
          (fn [w]
            (nth (iterate model/update-world w) n)))
   ;; render current state in ASCII
   (ascii/render @world)))


(defn play-ascii!
  "Animate the ASCII simulation in terminal
   frames: number of frames to run
   delay-ms: milliseconds between frames (default 100)"
  ([frames] (play-ascii! frames 100))
  ([frames delay-ms]
   (dotimes [_ frames]
     (step!)
     (Thread/sleep delay-ms)
     ;; clear screen for nicer effect
     (print (str (char 27) "[2J")) ; ANSI escape: clear screen
     (print (str (char 27) "[;H"))))) ; move cursor home

;; --------------------------
;; GUI MANAGEMENT
;; --------------------------
(defn start-gui! []
  (when-not @gui-started?
    (quil/run-gui)
    (reset! gui-started? true)))

;; --------------------------
;; MAIN ENTRY
;; --------------------------
(defn -main
  [& _args]
  ;; By default, just render current world in ASCII
  (ascii/render @world))

