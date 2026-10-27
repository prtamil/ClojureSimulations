(ns sim.ascii
  (:require
   [sim.model :as model]))

(defn empty-grid []
  (vec (repeat model/height
               (vec (repeat model/width \space)))))

(defn place-char [grid x y ch]
  (if (and (<= 0 x (dec model/width))
           (<= 0 y (dec model/height)))
    (assoc-in grid [y x] ch)
    grid))

(defn place-drop [grid {:keys [col row length]}]
  (reduce
   (fn [g dy]
     (let [y (- row dy)]
       (place-char g col y (rand-nth model/charset))))
   grid
   (range length)))

(defn place-drops [grid drops]
  (reduce place-drop grid drops))

(defn render [{:keys [t drops]}]
  (println "\nT =" t)
  (doseq [row (place-drops (empty-grid) drops)]
    (println (apply str row))))
