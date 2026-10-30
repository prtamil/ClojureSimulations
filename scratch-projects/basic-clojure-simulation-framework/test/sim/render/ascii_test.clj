(ns sim.render.ascii-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.model.core :as model]
   [sim.render.ascii :as ascii]))

(deftest seeded-preview-preserves-the-original-picture
  (let [world (model/advance-world-steps
               (model/initial-world model/default-seed)
               8
               (/ 1.0 60.0))
        rendered (ascii/render-string world)]
    (is (= 875 (count rendered)))
    (is (= 1443432906 (.hashCode ^String rendered)))))
