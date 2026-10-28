(ns sim.model.ball-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.model.ball :as ball]
   [sim.model.config :as config]))

(deftest construction-derives-velocity-from-direction
  (let [{:keys [vx vy radius]} (ball/create config/default-config)]
    (is (< (Math/abs (- vx vy)) 1.0e-12))
    (is (= 0.45 radius))))

(deftest an-axis-consumes-multiple-wall-hits
  (let [[position velocity] (ball/advance-axis 5.0 10.0 10.0 0.0 10.0)]
    (is (= 5.0 position))
    (is (= 10.0 velocity))))

(deftest radius-aware-motion-preserves-other-ball-data
  (let [before (assoc (ball/create config/default-config) :label :example)
        after (ball/advance config/default-config 1.0 0.25 before)]
    (is (= :example (:label after)))
    (is (not= (select-keys before [:x :y])
              (select-keys after [:x :y])))))
