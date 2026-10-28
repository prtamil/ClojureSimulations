(ns sim.utility.random-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.utility.random :as random]))

(defn- sample-ints [seed bound n]
  (loop [state seed
         remaining n
         values []]
    (if (zero? remaining)
      values
      (let [[state' value] (random/next-int state bound)]
        (recur state' (dec remaining) (conj values value))))))

(deftest random-stream-is-deterministic-and-well-mixed
  (let [values (sample-ints 42 4 64)]
    (is (= values (sample-ints 42 4 64)))
    (is (every? #(<= 0 % 3) values))
    (is (= #{0 1 2 3} (set values)))))

(deftest shuffled-permutation-is-doubled-and-complete
  (let [[_ permutation] (random/shuffled-permutation 2026)
        first-half (subvec permutation 0 256)
        second-half (subvec permutation 256 512)]
    (is (= 512 (count permutation)))
    (is (= first-half second-half))
    (is (= (set (range 256)) (set first-half)))))

(deftest random-bounds-are-validated
  (is (thrown? clojure.lang.ExceptionInfo (random/next-int 1 0)))
  (is (thrown? clojure.lang.ExceptionInfo (random/between 1 2.0 2.0))))

(deftest continuous-samples-stay-inside-their-ranges
  (let [[state unit] (random/unit 42)
        [_ ranged] (random/between state -3.0 7.0)]
    (is (<= 0.0 unit))
    (is (< unit 1.0))
    (is (<= -3.0 ranged))
    (is (< ranged 7.0))))

