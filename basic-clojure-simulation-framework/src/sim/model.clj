(ns sim.model)

;; -------------------------
;; CONFIG
;; -------------------------

(def width  40)
(def height 20)

;; -------------------------
;; MODEL (PURE DATA)
;; -------------------------

(defn initial-world []
  {:t 0
   :particles
   [{:id 1 :x 5  :y 10 :vx 1 :vy 0}
    {:id 2 :x 20 :y 5  :vx 0 :vy 1}]})

(defn move [{:keys [x y vx vy] :as p}]
  (-> p
      (update :x + vx)
      (update :y + vy)))

(defn clamp [{:keys [x y] :as p}]
  (assoc p
         :x (mod x width)
         :y (mod y height)))

(defn update-world [world]
  (-> world
      (update :t inc)
      (update :particles
              (fn [ps]
                (->> ps
                     (map move)
                     (map clamp)
                     vec)))))
