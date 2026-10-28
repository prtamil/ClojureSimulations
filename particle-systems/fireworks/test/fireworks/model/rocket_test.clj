;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.model.rocket-test
  (:require
   [clojure.test :refer [deftest is]]
   [fireworks.model.config :as config]
   [fireworks.model.rocket :as rocket]))

(def dt (/ 1.0 30.0))

(deftest waiting-rockets-launch-into-domain-values
  (let [[_ launched]
        (rocket/advance config/default-config 42 dt (rocket/waiting 7 0.0))]
    (is (= 7 (:id launched)))
    (is (= :ascending (:phase launched)))
    (is (= 37.0 (second (:position launched))))
    (is (contains? (set (map - (get-in config/default-config
                                      [:rockets :launch-speeds])))
                   (second (:velocity launched))))
    (is (<= 0 (:color-index launched) 6))))

(deftest ascending-rockets-become-bursts
  (let [ready {:id 3
               :phase :ascending
               :position [10.0 10.0]
               :velocity [0.0 -0.01]
               :color-index 2}
        [_ burst] (rocket/advance config/default-config 99 dt ready)]
    (is (= :bursting (:phase burst)))
    (is (= 80 (count (:sparks burst))))
    (is (every? #(= [10.0 (- 10.0 (* 0.01 dt))] (:position %))
                (:sparks burst)))))

(deftest completed-bursts-wait-before-relaunching
  (let [[_ waiting]
        (rocket/advance config/default-config 101 dt
                        {:id 4 :phase :bursting :sparks []})]
    (is (= :waiting (:phase waiting)))
    (is (<= 0.5 (:delay-seconds waiting)))
    (is (< (:delay-seconds waiting) 2.5))))
