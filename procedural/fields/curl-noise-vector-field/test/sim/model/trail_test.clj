(ns sim.model.trail-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.model.config :as config]
   [sim.model.trail :as trail]))

(deftest deposits-create-semantic-cells
  (let [cells (trail/deposit config/default-config {}
                             [{:position [2 3] :band 1}
                              {:position [5 8] :band 2}])]
    (is (= #{[2 3] [5 8]} (set (keys cells))))
    (is (= {:position [2 3] :intensity 1.0 :band 1}
           (get cells [2 3])))))

(deftest fading-is-time-based-and-sparse
  (let [cells {[2 3] {:position [2 3] :intensity 1.0 :band 1}}
        faded (trail/fade config/default-config cells 1.0)
        gone (trail/fade config/default-config cells 100.0)]
    (is (< (:intensity (get faded [2 3])) 1.0))
    (is (empty? gone))))
