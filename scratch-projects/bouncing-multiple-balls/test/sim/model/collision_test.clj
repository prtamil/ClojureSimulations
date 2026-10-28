(ns sim.model.collision-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.model.collision :as collision]))

(def left
  {:id 0 :x 10.0 :y 10.0 :vx 1.0 :vy 0.0
   :radius 0.5 :mass 0.25 :color 0})

(def right
  {:id 1 :x 11.0 :y 10.0 :vx -1.0 :vy 0.0
   :radius 0.5 :mass 0.25 :color 1})

(deftest equal-mass-head-on-collision-exchanges-velocities
  (let [[after-left after-right] (collision/resolve-pair 1.0 left right)]
    (is (= -1.0 (:vx after-left)))
    (is (= 1.0 (:vx after-right)))
    (is (= 0.0 (:vy after-left) (:vy after-right)))))

(deftest separated-bodies-are-unchanged
  (is (= [left (assoc right :x 20.0)]
         (collision/resolve-pair 1.0 left (assoc right :x 20.0)))))
