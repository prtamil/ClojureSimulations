(ns sim.render.projection-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.model.core :as model]
   [sim.render.projection :as projection]
   [sim.utility.theme :as theme]))

(deftest projected-scene-has-one-explicit-shape
  (let [scene (projection/project-world (model/initial-world 42))]
    (is (= #{:pattern :samples :particles :flash-intensity}
           (set (keys scene))))))

(deftest particle-views-project-trails-and-visible-particle-data
  (let [world (model/advance-world (model/initial-world 42))
        scene (projection/project-world world)]
    (is (= :particles (:pattern scene)))
    (is (= (:trail world) (:samples scene)))
    (is (= (count (:particles world)) (count (:particles scene))))
    (is (every? #(= #{:position :band} (set (keys %)))
                (:particles scene)))))

(deftest static-views-project-samples-without-particles
  (let [world (model/cycle-view (model/initial-world 42) 1)
        scene (projection/project-world world)]
    (is (= :vector (:pattern scene)))
    (is (= (get-in world [:view :samples]) (:samples scene)))
    (is (seq (:samples scene)))
    (is (empty? (:particles scene)))))

(deftest presentation-changes-do-not-recalculate-the-scene
  (let [world (model/advance-world (model/initial-world 42))]
    (is (= (projection/project-world world)
           (projection/project-world (theme/cycle-theme world 1))))))
