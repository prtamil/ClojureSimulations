;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.model.firework-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [fireworks.model.config :as config]
   [fireworks.model.firework :as firework]))

(def dt (/ 1.0 30.0))

(deftest waiting-fireworks-launch-into-domain-values
  (let [waiting (firework/wait-before-launch 7 0.0)
        [_ launched]
        (firework/advance-firework config/default-config 42 dt
                                   waiting)]
    (is (= {:id 7 :stage :waiting :delay-seconds 0.0} waiting))
    (is (= 7 (:id launched)))
    (is (= :flying (:stage launched)))
    (is (= 37.0 (second (:position launched))))
    (is (contains? (set (map - (get-in config/default-config
                                       [:rockets :launch-speeds])))
                   (second (:velocity launched))))
    (is (<= 0 (:color-index launched) 6))))

(deftest flying-fireworks-become-bursts
  (let [ready {:id 3
               :stage :flying
               :position [10.0 10.0]
               :velocity [0.0 -0.01]
               :color-index 2}
        [_ burst]
        (firework/advance-firework config/default-config 99 dt ready)]
    (is (= :bursting (:stage burst)))
    (is (= 80 (count (:sparks burst))))
    (is (every? #(= [10.0 (- 10.0 (* 0.01 dt))] (:position %))
                (:sparks burst)))))

(deftest completed-bursts-wait-before-relaunching
  (let [[_ waiting]
        (firework/advance-firework
         config/default-config 101 dt
         {:id 4 :stage :bursting :sparks []})]
    (is (= :waiting (:stage waiting)))
    (is (<= 0.5 (:delay-seconds waiting)))
    (is (< (:delay-seconds waiting) 2.5))))

(deftest unknown-stages-explain-the-invalid-firework
  (testing "stage dispatch stays explicit"
    (let [error (try
                  (firework/advance-firework config/default-config 1 dt
                                             {:id 9 :stage :unknown})
                  nil
                  (catch clojure.lang.ExceptionInfo error error))]
      (is (= {:firework-id 9 :stage :unknown} (ex-data error))))))
