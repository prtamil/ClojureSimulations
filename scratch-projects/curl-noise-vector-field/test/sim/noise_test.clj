(ns sim.noise-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.config :as config]
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

(deftest curl-samples-are-finite
  (doseq [warped? [false true]]
    (let [[vx vy] (noise/curl-at config/default-config
                                 permutation 1.2 3.4 0.5 warped?)]
      (is (Double/isFinite vx))
      (is (Double/isFinite vy)))))

(deftest domain-warping-changes-the-potential
  (is (not= (noise/potential-at permutation 2.3 4.5 0.7)
            (noise/warped-potential-at permutation 2.3 4.5 0.7))))

