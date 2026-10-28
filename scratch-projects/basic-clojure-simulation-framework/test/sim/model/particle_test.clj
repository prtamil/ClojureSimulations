(ns sim.model.particle-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.model.config :as config]
   [sim.model.particle :as particle]))

(deftest particles-move-in-seconds-and-wrap
  (let [moved (particle/advance config/default-config 0.5
                                {:id 1 :x 39.0 :y 19.0
                                 :vx 4.0 :vy 3.0})]
    (is (= 1.0 (:x moved)))
    (is (= 0.5 (:y moved)))))

(deftest collections-are-transformed-without-mutation
  (let [particles (get-in config/default-config [:particles :initial])
        moved (particle/advance-all config/default-config particles 0.25)]
    (is (= 2 (count moved)))
    (is (not= particles moved))
    (is (= particles (get-in config/default-config [:particles :initial])))))
