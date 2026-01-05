(ns sim.ascii
  (:require
   [sim.model :as model]))

(defn empty-grid []
  (vec (repeat model/height
              (vec (repeat model/width \.)))))

(defn place-ball [grid {:keys [x y]}]
  ;; ensure indices are integers
  (let [ix (int x)
        iy (int y)]
    (assoc-in grid [iy ix] \#)))

(defn render [{:keys [t ball]}]
  (println "\nT =" t)
  (doseq [row (place-ball (empty-grid) ball)]
    (println (apply str row))))


