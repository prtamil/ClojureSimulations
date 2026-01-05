(ns sim.quil
  (:require
   [quil.core :as q]
   [quil.middleware :as m]
   [sim.model :as model]))

(def scale 15)

(defn setup []
  (q/frame-rate 60)
  (model/initial-world))

(defn update [world]
  (model/update-world world))

(defn draw [{:keys [ball]}]
  (q/background 245)
  (q/fill 20)

  (let [{:keys [x y]} ball]
     (q/ellipse
      (* x scale)
      (* y scale)
      scale
      scale)))

(defn run-gui []
  (q/defsketch simulation
    :title "Bouncing Ball"
    :size [(* model/width scale)
           (* model/height scale)]
    :setup setup
    :update update
    :draw draw
    :middleware [m/fun-mode]))

