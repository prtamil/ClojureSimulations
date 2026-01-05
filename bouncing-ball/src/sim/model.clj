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
   :ball {:x 10
         :y 5
         :speed 1.0
         :dir (/ Math/PI 4)}}) ; 45°

(defn dir->vel [speed dir]
  {:vx (* speed (Math/cos dir))
   :vy (* speed (Math/sin dir))})


(defn move-ball [{:keys [x y speed dir] :as ball}]
  (let [{:keys [vx vy]} (dir->vel speed dir)]
    (-> ball
        (update :x + vx)
        (update :y + vy))))

(defn bounce [{:keys [x y dir] :as ball}]
  (cond
    ;; left or right wall
    (or (<= x 0) (>= x (dec width)))
    (assoc ball :dir (- Math/PI dir))

    ;; top or bottom wall
    (or (<= y 0) (>= y (dec height)))
    (assoc ball :dir (- dir))

    :else
    ball))

(defn clamp-ball [{:keys [x y] :as ball}]
  (assoc ball
         :x (-> x (max 0) (min (dec width)))
         :y (-> y (max 0) (min (dec height)))))

(defn update-ball [ball]
  (-> ball
      move-ball
      bounce
      clamp-ball))

(defn update-world [world]
  (-> world
      (update :t inc)
      (update :ball update-ball)))

