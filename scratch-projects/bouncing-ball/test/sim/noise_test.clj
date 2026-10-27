(ns sim.noise-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.noise :as noise]
   [sim.random :as random]))

(def permutation
  (second (random/shuffled-permutation 2026)))

(deftest perlin-noise-is-deterministic-and-periodic
  (let [sample (noise/perlin-2d permutation 1.25 7.75)]
    (is (= sample (noise/perlin-2d permutation 1.25 7.75)))
    (is (< (Math/abs (- sample
                        (noise/perlin-2d permutation (+ 256.0 1.25) 7.75)))
           1.0e-12))))

(deftest scaled-fbm-samples-are-finite
  (doseq [[x y] [[0.0 0.0] [12.0 8.0] [-3.5 400.25]]]
    (is (Double/isFinite (noise/sample-2d permutation x y)))))
