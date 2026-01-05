(ns sim.ascii
  (:require
   [sim.model :as model]))

(defn empty-grid []
  (vec (repeat model/height (vec (repeat model/width \.)))))

(defn place-particles [grid particles]
  (reduce
   (fn [g {:keys [x y]}]
     (assoc-in g [y x] \#))
   grid
   particles))

(defn render [{:keys [t particles]}]
  (println "\nT =" t)
  (doseq [row (place-particles (empty-grid) particles)]
    (println (apply str row))))
