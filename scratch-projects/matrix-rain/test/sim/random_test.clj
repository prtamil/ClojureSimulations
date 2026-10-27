(ns sim.random-test
  (:require [clojure.test :refer [deftest is]]
            [sim.random :as random]))

(defn- sample [seed f n]
  (loop [state seed remaining n values []]
    (if (zero? remaining)
      values
      (let [[state' value] (f state)]
        (recur state' (dec remaining) (conj values value))))))

(deftest random-stream-is-repeatable-and-bounded
  (let [ints (sample 42 #(random/next-int % 4) 64)
        doubles (sample 42 random/next-double 64)]
    (is (= ints (sample 42 #(random/next-int % 4) 64)))
    (is (= doubles (sample 42 random/next-double 64)))
    (is (= #{0 1 2 3} (set ints)))
    (is (every? #(<= 0.0 % 1.0) doubles))
    (is (every? #(< % 1.0) doubles))))

(deftest shuffled-permutation-is-complete-and-doubled
  (let [[_ permutation] (random/shuffled-permutation 2026)
        first-half (subvec permutation 0 256)]
    (is (= 512 (count permutation)))
    (is (= first-half (subvec permutation 256)))
    (is (= (set (range 256)) (set first-half)))))

(deftest random-bounds-are-validated
  (is (thrown? clojure.lang.ExceptionInfo (random/next-int 1 0))))
