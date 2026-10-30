(ns sim.model.collision-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.model.collision :as collision]))

(def left
  {:id 0 :x 10.0 :y 10.0 :vx 1.0 :vy 0.0
   :radius 0.5 :mass 0.25 :band-index 0})

(def right
  {:id 1 :x 11.0 :y 10.0 :vx -1.0 :vy 0.0
   :radius 0.5 :mass 0.25 :band-index 1})

(defn- near? [left right]
  (< (Math/abs (- (double left) (double right))) 1.0e-9))

(defn- momentum [balls]
  [(reduce + (map #(* (:mass %) (:vx %)) balls))
   (reduce + (map #(* (:mass %) (:vy %)) balls))])

(defn- kinetic-energy [balls]
  (reduce + (map #(* 0.5 (:mass %)
                     (+ (* (:vx %) (:vx %)) (* (:vy %) (:vy %))))
                 balls)))

(deftest equal-mass-head-on-collision-exchanges-velocities
  (let [[after-left after-right] (collision/resolve-pair 1.0 left right)]
    (is (= -1.0 (:vx after-left)))
    (is (= 1.0 (:vx after-right)))
    (is (= 0.0 (:vy after-left) (:vy after-right)))))

(deftest separated-bodies-are-unchanged
  (is (= [left (assoc right :x 20.0)]
         (collision/resolve-pair 1.0 left (assoc right :x 20.0)))))

(deftest elastic-collision-conserves-momentum-and-energy
  (let [a (assoc left :vx 3.0 :vy 1.0 :mass 1.0)
        b (assoc right :x 10.9 :vx -1.0 :vy -2.0 :mass 2.0)
        before [a b]
        after (collision/resolve-pair 1.0 a b)
        [before-x before-y] (momentum before)
        [after-x after-y] (momentum after)]
    (is (near? before-x after-x))
    (is (near? before-y after-y))
    (is (near? (kinetic-energy before) (kinetic-energy after)))))

(deftest overlapping-but-receding-balls-only-separate
  (let [a (assoc left :vx -1.0)
        b (assoc right :x 10.9 :vx 1.0)
        [after-a after-b] (collision/resolve-pair 1.0 a b)]
    (is (= (:vx a) (:vx after-a)))
    (is (= (:vx b) (:vx after-b)))))
