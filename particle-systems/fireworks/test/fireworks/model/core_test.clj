;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.model.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [fireworks.model.core :as model]))

(def dt (/ 1.0 30.0))

(defn- firework-positions [world]
  (mapcat (fn [firework]
            (case (:stage firework)
              :flying [(:position firework)]
              :bursting (map :position (:sparks firework))
              []))
          (get-in world [:show :fireworks])))

(defn- finite-position? [position]
  (every? #(and (number? %) (Double/isFinite (double %))) position))

(deftest empty-values-show-the-complete-world-shape
  (is (= {:tick 0
          :elapsed-seconds 0.0
          :paused? false
          :simulation-fps 0}
         model/empty-clock))
  (is (= {:fireworks [] :next-id 0} model/empty-show))
  (is (= {:config nil
          :seed 0
          :random-state 0
          :theme-index 0
          :clock model/empty-clock
          :show model/empty-show}
         model/empty-world)))

(deftest initial-world-is-explicit-and-deterministic
  (let [world (model/initial-world 42)
        fireworks (get-in world [:show :fireworks])
        delays (mapv :delay-seconds fireworks)]
    (is (= world (model/initial-world 42)))
    (is (= 6 (model/firework-count world)))
    (is (= 6 (get-in world [:show :next-id])))
    (is (every? #(= :waiting (:stage %)) fireworks))
    (is (= 0.0 (first delays)))
    (is (every? true?
                (map #(< (Math/abs (- %1 %2)) 1.0e-12)
                     delays
                     (mapv #(* % (/ 8.0 30.0)) (range 6)))))))

(deftest world-steps-launch-fireworks-and-advance-time
  (let [world (model/initial-world 42)
        launched (model/advance-world world dt)]
    (is (= 1 (get-in launched [:clock :tick])))
    (is (< (Math/abs (- dt (get-in launched [:clock :elapsed-seconds])))
           1.0e-12))
    (is (= :flying (get-in launched [:show :fireworks 0 :stage])))
    (is (= :waiting (get-in launched [:show :fireworks 1 :stage])))
    (is (not= (:random-state world) (:random-state launched)))))

(deftest controls-and-resize-preserve-preferences
  (let [world (model/initial-world 9)
        paused (model/toggle-paused world)]
    (is (= paused (model/advance-world-steps paused 20 dt)))
    (is (= 60 (get-in (model/change-simulation-speed world 1000)
                      [:clock :simulation-fps])))
    (is (= 10 (get-in (model/change-simulation-speed world -1000)
                      [:clock :simulation-fps])))
    (is (= 1 (model/firework-count
              (model/change-firework-count world -1000))))
    (is (= 20 (model/firework-count
               (model/change-firework-count world 1000))))
    (let [expanded (model/change-firework-count world 1)
          added (peek (get-in expanded [:show :fireworks]))]
      (is (= :waiting (:stage added)))
      (is (= 6 (:id added)))
      (is (= 7 (get-in expanded [:show :next-id]))))
    (let [resized (model/resize-world world 60 22)]
      (is (= 60 (get-in resized [:config :grid :width])))
      (is (= 22 (get-in resized [:config :grid :height])))
      (is (= (:clock world) (:clock resized)))
      (is (= (model/firework-count world)
             (model/firework-count resized))))))

(deftest presentation-changes-do-not-recalculate-the-show
  (let [world (model/advance-world-steps (model/initial-world 44) 50 dt)
        themed (model/cycle-theme world 1)]
    (is (= (:show world) (:show themed)))
    (is (= (:random-state world) (:random-state themed)))
    (is (not= (:theme-index world) (:theme-index themed)))))

(deftest restart-reconstructs-the-show-without-resetting-preferences
  (let [world (-> (model/initial-world 123)
                  (model/advance-world-steps 50 dt)
                  (model/cycle-theme 1)
                  (model/change-simulation-speed 5))
        restarted (model/restart-show world)]
    (is (= 0 (get-in restarted [:clock :tick])))
    (is (= 0.0 (get-in restarted [:clock :elapsed-seconds])))
    (is (= (:seed restarted) (:random-state restarted)))
    (is (= 1 (:theme-index restarted)))
    (is (= 35 (get-in restarted [:clock :simulation-fps])))
    (is (= (:show (model/initial-world 123)) (:show restarted)))))

(deftest long-seeded-runs-remain-reproducible-and-finite
  (let [left (model/advance-world-steps (model/initial-world 1234) 240 dt)
        right (model/advance-world-steps (model/initial-world 1234) 240 dt)]
    (is (= left right))
    (is (= 240 (get-in left [:clock :tick])))
    (is (some #(not= :waiting (:stage %))
              (get-in left [:show :fireworks])))
    (is (every? finite-position? (firework-positions left)))))

(deftest invalid-step-input-is-rejected
  (let [world (model/initial-world)]
    (testing "negative step batch"
      (is (thrown? clojure.lang.ExceptionInfo
                   (model/advance-world-steps world -1 dt))))
    (testing "non-positive dt"
      (is (thrown? clojure.lang.ExceptionInfo
                   (model/advance-world-steps world 1 0.0))))))
