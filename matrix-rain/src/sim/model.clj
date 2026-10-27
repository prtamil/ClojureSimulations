(ns sim.model)

;; -------------------------
;; CONFIG
;; -------------------------

(def width  40)
(def height 20)

;; Characters used in the rain
(def charset
  (map char (range 33 127))) ; visible ASCII

;; How many streams exist at once
(def drop-count 15)

;; -------------------------
;; DROP (PURE DATA)
;; -------------------------
;; A drop represents the *head* of a vertical stream

;; {:col    Int   ; x position
;;  :row    Int   ; y position (head)
;;  :speed  Int   ; rows per tick
;;  :length Int}  ; trail length

(defn random-drop []
  {:col    (rand-int width)
   ;; start above the screen so streams "enter"
   :row    (- (rand-int height))
   :speed  (+ 1 (rand-int 2))
   :length (+ 5 (rand-int 10))})

;; -------------------------
;; WORLD
;; -------------------------

(defn initial-world []
  {:t 0
   :drops (vec (repeatedly drop-count random-drop))})

;; -------------------------
;; UPDATE LOGIC
;; -------------------------

(defn reset-drop [drop]
  ;; Respawn at top with new properties
  (assoc (random-drop) :row 0))

(defn update-drop [{:keys [row speed length] :as drop}]
  (let [new-row (+ row speed)]
    ;; when tail has fully left screen → reset
    (if (> (- new-row length) height)
      (reset-drop drop)
      (assoc drop :row new-row))))

(defn update-world [world]
  (-> world
      (update :t inc)
      (update :drops #(mapv update-drop %))))
