;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.model.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [leaf-fall.model.config :as config]
   [leaf-fall.model.core :as model]
   [leaf-fall.render.cells :as cells]))

(def quick-config
  (-> config/default-config
      (assoc :grid {:width 32 :height 14})
      (assoc-in [:phase :display-seconds] 0.05)
      (assoc-in [:phase :reset-seconds] 0.05)
      (assoc-in [:fall :start-delay-max-seconds] 0.01)
      (assoc-in [:fall :speed-options] [100.0])
      (assoc-in [:fall :trail-length] 1)))

(defn- advance-until [world predicate limit]
  (loop [world world
         remaining limit]
    (cond
      (predicate world) world
      (zero? remaining) world
      :else (recur (model/update-world world 0.01) (dec remaining)))))

(deftest initial-world-is-complete-and-deterministic
  (let [world (model/initial-world 42)]
    (is (= world (model/initial-world 42)))
    (is (= :display (:phase world)))
    (is (= 1 (:cycle world)))
    (is (map? (:cells world)))
    (is (pos? (count (:leaves world))))
    (is (pos? (count (cells/visible-cells world))))))

(deftest lifecycle-visits-falling-reset-and-next-tree
  (let [display (model/initial-world quick-config 7)
        falling (model/update-world-steps display 5 0.01)
        resetting (advance-until falling #(= :reset (:phase %)) 1000)
        next-tree (model/update-world-steps resetting 6 0.01)]
    (is (= :falling (:phase falling)))
    (is (= (count (:leaves display)) (count (:falling-leaves falling))))
    (is (= :reset (:phase resetting)))
    (is (empty? (cells/visible-cells resetting)))
    (is (= :display (:phase next-tree)))
    (is (= 2 (:cycle next-tree)))
    (is (not= (:seed display) (:seed next-tree)))))

(deftest falling-leaves-produce-heads-and-trails
  (let [world (-> (model/initial-world quick-config 19)
                  model/skip-phase
                  (model/update-world-steps 4 0.01))
        roles (set (map :role (cells/visible-cells world)))]
    (is (= :falling (:phase world)))
    (is (contains? roles :fall-head))
    (is (contains? roles :fall-trail))))

(deftest controls-preserve-pure-world-behavior
  (let [world (model/initial-world 42)]
    (testing "pause freezes all model state"
      (let [paused (model/toggle-paused world)]
        (is (= paused (model/update-world-steps paused 100 0.01)))))
    (testing "skip advances the lifecycle"
      (is (= :falling (:phase (model/skip-phase world))))
      (is (= 2 (:cycle (-> world model/skip-phase model/skip-phase)))))
    (testing "simulation rate is clamped"
      (is (= 120 (:sim-fps (model/adjust-sim-fps world 1000))))
      (is (= 20 (:sim-fps (model/adjust-sim-fps world -1000)))))
    (is (thrown? clojure.lang.ExceptionInfo
                 (model/update-world-steps world -1 0.1)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (model/update-world-steps world 1 0.0)))))
