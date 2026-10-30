(ns sim.model.ball-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.model.ball :as ball]
   [sim.model.config :as config]))

(deftest seeded-spawning-is-repeatable-and-non-overlapping
  (let [[state balls] (ball/spawn-many config/default-config 42)
        [same-state same-balls] (ball/spawn-many config/default-config 42)]
    (is (= state same-state))
    (is (= balls same-balls))
    (is (= (get-in config/default-config [:balls :count]) (count balls)))
    (is (not-any? true?
                  (for [left (range (count balls))
                        right (range (inc left) (count balls))]
                    (ball/overlap? (nth balls left) (nth balls right)))))))

(deftest wall-motion-preserves-the-domain
  (let [before {:id 1 :x 39.0 :y 10.0 :vx 20.0 :vy 0.0
                :radius 0.5 :mass 0.25 :band-index 0}
        after (ball/advance config/default-config 1.0 0.2 before)]
    (is (<= (:radius after) (:x after)
            (- (config/grid-width config/default-config) (:radius after))))
    (is (neg? (:vx after)))))
