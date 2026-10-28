;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.model.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [fireworks.model.core :as model]))

(def dt (/ 1.0 30.0))

(deftest initial-world-is-minimal-and-deterministic
  (let [world (model/initial-world 42)
        delays (mapv :delay-seconds (:rockets world))]
    (is (= world (model/initial-world 42)))
    (is (= 6 (model/rocket-count world)))
    (is (= 6 (:next-rocket-id world)))
    (is (every? #(= :waiting (:phase %)) (:rockets world)))
    (is (= 0.0 (first delays)))
    (is (every? true?
                (map #(< (Math/abs (- %1 %2)) 1.0e-12)
                     delays
                     (mapv #(* % (/ 8.0 30.0)) (range 6)))))))

(deftest world-steps-launch-rockets-and-advance-time
  (let [world (model/initial-world 42)
        launched (model/update-world world dt)]
    (is (= 1 (:tick launched)))
    (is (< (Math/abs (- dt (:elapsed-seconds launched))) 1.0e-12))
    (is (= :ascending (-> launched :rockets first :phase)))
    (is (= :waiting (-> launched :rockets second :phase)))
    (is (not= (:rng-state world) (:rng-state launched)))))

(deftest pause-controls-and-resize-preserve-preferences
  (let [world (model/initial-world 9)
        paused (model/toggle-paused world)]
    (is (= paused (model/update-world-steps paused 20 dt)))
    (is (= 60 (:sim-fps (model/adjust-sim-fps world 1000))))
    (is (= 10 (:sim-fps (model/adjust-sim-fps world -1000))))
    (is (= 1 (model/rocket-count
              (model/adjust-rocket-count world -1000))))
    (is (= 20 (model/rocket-count
               (model/adjust-rocket-count world 1000))))
    (let [expanded (model/adjust-rocket-count world 1)
          added (peek (:rockets expanded))]
      (is (= :waiting (:phase added)))
      (is (= 6 (:id added)))
      (is (= 7 (:next-rocket-id expanded))))
    (let [resized (model/resize-world world 60 22)]
      (is (= 60 (get-in resized [:config :grid :width])))
      (is (= 22 (get-in resized [:config :grid :height])))
      (is (= (:sim-fps world) (:sim-fps resized)))
      (is (= (model/rocket-count world) (model/rocket-count resized))))))

(deftest reset-reconstructs-the-show-without-resetting-preferences
  (let [world (-> (model/initial-world 123)
                  (model/update-world-steps 50 dt)
                  (model/cycle-theme 1)
                  (model/adjust-sim-fps 5))
        reset (model/reset-show world)]
    (is (= 0 (:tick reset)))
    (is (= 0.0 (:elapsed-seconds reset)))
    (is (= (:seed reset) (:rng-state reset)))
    (is (= 1 (:theme-index reset)))
    (is (= 35 (:sim-fps reset)))
    (is (= (:rockets (model/initial-world 123)) (:rockets reset)))))

(deftest long-seeded-runs-remain-reproducible
  (let [left (model/update-world-steps (model/initial-world 1234) 240 dt)
        right (model/update-world-steps (model/initial-world 1234) 240 dt)]
    (is (= left right))
    (is (= 240 (:tick left)))
    (is (some #(not= :waiting (:phase %)) (:rockets left)))))

(deftest invalid-step-input-is-rejected
  (let [world (model/initial-world)]
    (testing "negative tick batch"
      (is (thrown? clojure.lang.ExceptionInfo
                   (model/update-world-steps world -1 dt))))
    (testing "non-positive dt"
      (is (thrown? clojure.lang.ExceptionInfo
                   (model/update-world-steps world 1 0.0))))))
