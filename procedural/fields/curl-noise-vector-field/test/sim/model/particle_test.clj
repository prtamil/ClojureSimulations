(ns sim.model.particle-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.model.config :as config]
   [sim.model.field :as field]
   [sim.model.particle :as particle]
   [sim.utility.random :as random]))

(def field-value
  (field/create (second (random/shuffled-permutation 42))))

(deftest spawning-is-seeded-and-inside-the-domain
  (let [[rng particles] (particle/spawn-many config/default-config 7)
        [same-rng same-particles] (particle/spawn-many config/default-config 7)]
    (is (= rng same-rng))
    (is (= particles same-particles))
    (is (= (get-in config/default-config [:particles :count])
           (count particles)))
    (is (every? #(particle/in-bounds? config/default-config (:position %))
                particles))))

(deftest motion-and-age-use-elapsed-seconds
  (let [[_ particle] (particle/spawn config/default-config 9)
        moved (particle/advance config/default-config field-value
                                8 false 0.25 particle)]
    (is (= 0.25 (:age-seconds moved)))
    (is (not= (:position particle) (:position moved)))))

(deftest advancing-a-population-produces-semantic-deposits
  (let [[rng particles] (particle/spawn-many config/default-config 11)
        result (particle/advance-all config/default-config field-value
                                     8 false (/ 1.0 60.0) rng particles)]
    (is (= (count particles) (count (:particles result))))
    (is (seq (:deposits result)))
    (is (every? #(and (vector? (:position %)) (int? (first (:position %))))
                (:deposits result)))))
