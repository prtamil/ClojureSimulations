;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.collision
  "Pairwise separation and elastic collision impulses.")

(def ^:private epsilon 1.0e-12)

(defn resolve-pair [restitution left right]
  (let [dx (- (:x right) (:x left))
        dy (- (:y right) (:y left))
        distance-squared (+ (* dx dx) (* dy dy))
        minimum-distance (+ (:radius left) (:radius right))]
    (if (> distance-squared (* minimum-distance minimum-distance))
      [left right]
      (let [distance (Math/sqrt distance-squared)
            [normal-x normal-y]
            (if (> distance epsilon)
              [(/ dx distance) (/ dy distance)]
              (if (< (:id left) (:id right)) [1.0 0.0] [-1.0 0.0]))
            inverse-left (/ 1.0 (:mass left))
            inverse-right (/ 1.0 (:mass right))
            inverse-total (+ inverse-left inverse-right)
            penetration (- minimum-distance distance)
            left-correction (/ (* penetration inverse-left) inverse-total)
            right-correction (/ (* penetration inverse-right) inverse-total)
            left (-> left
                     (update :x - (* normal-x left-correction))
                     (update :y - (* normal-y left-correction)))
            right (-> right
                      (update :x + (* normal-x right-correction))
                      (update :y + (* normal-y right-correction)))
            relative-x (- (:vx right) (:vx left))
            relative-y (- (:vy right) (:vy left))
            normal-speed (+ (* relative-x normal-x)
                            (* relative-y normal-y))]
        (if (neg? normal-speed)
          (let [impulse (/ (* (- (+ 1.0 restitution)) normal-speed)
                           inverse-total)
                impulse-x (* impulse normal-x)
                impulse-y (* impulse normal-y)]
            [(-> left
                 (update :vx - (* impulse-x inverse-left))
                 (update :vy - (* impulse-y inverse-left)))
             (-> right
                 (update :vx + (* impulse-x inverse-right))
                 (update :vy + (* impulse-y inverse-right)))])
          [left right])))))

(defn- pairs [count]
  (for [left (range count)
        right (range (inc left) count)]
    [left right]))

(defn resolve-all [restitution balls]
  (reduce (fn [balls [left-index right-index]]
            (let [[left right]
                  (resolve-pair restitution
                                (nth balls left-index)
                                (nth balls right-index))]
              (assoc balls left-index left right-index right)))
          balls
          (pairs (count balls))))
