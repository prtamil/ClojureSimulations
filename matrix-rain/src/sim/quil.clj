(ns sim.quil
  (:require
   [quil.core :as q]
   [quil.middleware :as m]
   [sim.model :as model]))

(def scale 15)

;; -------------------------
;; SETUP
;; -------------------------

(defn setup []
  (q/frame-rate 60)
  (q/color-mode :rgb)
  (model/initial-world))

;; -------------------------
;; UPDATE
;; -------------------------

(defn update [world]
  (model/update-world world))

;; -------------------------
;; DRAW
;; -------------------------

(defn draw-drop [{:keys [col row length]}]
  ;; draw trail from head downward
  (doseq [dy (range length)]
    (let [y (- row dy)]
      (when (and (>= y 0) (< y model/height))
        ;; head brighter than tail
        (if (zero? dy)
          (q/fill 180 255 180) ; bright green
          (q/fill 0 180 0))    ; darker green

        (q/rect (* col scale)
                (* y scale)
                scale
                scale)))))

(defn draw [{:keys [drops]}]
  (q/background 0)
  (doseq [drop drops]
    (draw-drop drop)))

;; -------------------------
;; RUN
;; -------------------------

(defn run-gui []
  (q/defsketch simulation
    :title "Matrix Rain"
    :size [(* model/width scale)
           (* model/height scale)]
    :setup setup
    :update update
    :draw draw
    :middleware [m/fun-mode]))
