(ns sim.model.field-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.model.config :as config]
   [sim.model.field :as field]
   [sim.utility.random :as random]))

(def permutation (second (random/shuffled-permutation 42)))

(deftest fields-advance-by-elapsed-time
  (let [before (field/create permutation)
        after (field/advance config/default-config before 2 0.5)]
    (is (= 0.0 (:time before)))
    (is (= 0.1 (:time after)))
    (is (= permutation (:permutation after)))))

(deftest static-views-are-semantic-cell-maps
  (doseq [pattern [:vector :potential :curl-magnitude]]
    (let [cells (field/sample-grid config/default-config
                                   (field/create permutation)
                                   pattern)]
      (is (seq cells))
      (is (every? (fn [[position cell]]
                    (= position (:position cell)))
                  cells))
      (is (every? #(<= 0.0 (:intensity %) 1.0) (vals cells))))))

(deftest curl-directions-are-finite
  (doseq [warped? [false true]]
    (let [[x y] (field/direction config/default-config
                                 (field/create permutation)
                                 [20.0 10.0]
                                 warped?)]
      (is (Double/isFinite x))
      (is (Double/isFinite y)))))
