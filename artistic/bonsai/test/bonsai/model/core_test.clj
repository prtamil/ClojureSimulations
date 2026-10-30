;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.model.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [bonsai.model.config :as config]
   [bonsai.model.core :as model]
   [bonsai.render.cells :as cells]
   [bonsai.utility.theme :as theme]))

(defn- cells-with-role [world role]
  (filter #(= role (:role %)) (vals (:cells world))))

(deftest seeded-worlds-are-complete-and-deterministic
  (let [first-world (model/initial-world 42)
        second-world (model/initial-world 42)]
    (is (= first-world second-world))
    (is (= #{:config :seed :generation :clock :style-index :theme-index
             :pot :tree :cells :wind :growth-progress}
           (set (keys first-world))))
    (is (map? (:cells first-world)))
    (is (< (count (:cells first-world))
           (config/cell-count (:config first-world))))
    (is (seq (get-in first-world [:tree :trunk :segments])))
    (is (seq (get-in first-world [:tree :trunk :branch-sites])))
    (is (seq (get-in first-world [:tree :crown :foliage])))
    (is (int? (get-in first-world [:pot :top-row])))
    (is (seq (cells-with-role first-world :pot)))
    (is (some #(= \: (:glyph %)) (cells-with-role first-world :pot)))))

(deftest every-classical-style-produces-a-tree
  (let [base (model/initial-world 9001)]
    (doseq [style-index (range (count config/style-order))
            :let [world (model/construct-tree
                         (assoc base :style-index style-index))]]
      (testing (name (model/current-style-key world))
        (is (pos? (count (get-in world [:tree :trunk :segments]))))
        (is (seq (cells-with-role world :trunk-dark)))))))

(deftest winter-is-a-bare-tree
  (let [winter-index
        (first (keep-indexed #(when (= "WINTER" (:name %2)) %1) theme/themes))
        base-world (model/initial-world 42)]
    (doseq [style-index (range (count config/style-order))
            :let [winter (model/construct-tree
                          (assoc base-world
                                 :style-index style-index
                                 :theme-index winter-index))]]
      (testing (name (model/current-style-key winter))
        (is (seq (get-in winter [:tree :crown :foliage])))
        (is (empty? (cells-with-role winter :foliage)))
        (is (seq (get-in winter [:tree :crown :tips])))
        (is (seq (cells-with-role winter :snow-tip)))
        (is (every? #(= \* (:glyph %))
                    (cells-with-role winter :snow-tip)))
        (is (seq (cells-with-role winter :trunk-dark)))))))

(deftest controls-wrap-clamp-and-rebuild
  (let [world (model/initial-world 42)
        previous-style (model/cycle-style world -2)
        previous-theme (model/cycle-theme world -1)
        new-tree (model/reseed world)]
    (is (= :bunjin (model/current-style-key previous-style)))
    (is (= "MONO" (:name (theme/current-theme previous-theme))))
    (is (not= (:seed world) (:seed new-tree)))
    (is (= 1 (:generation new-tree)))
    (is (zero? (:growth-progress new-tree)))
    (is (= 3 (:wind (model/adjust-wind world 99))))
    (is (= 0 (:wind (model/adjust-wind world -99))))
    (is (= 120 (get-in (model/change-simulation-fps world 999)
                       [:clock :simulation-fps])))
    (is (= 10 (get-in (model/change-simulation-fps world -999)
                      [:clock :simulation-fps])))))

(deftest time-pause-and-growth-are-pure-transitions
  (let [world (model/replay-growth (model/initial-world 42))
        advanced (model/advance-world-steps world 30 (/ 1.0 30.0))
        grown (model/advance-world-steps world 300 (/ 1.0 30.0))
        paused (model/toggle-paused advanced)]
    (is (= 30 (get-in advanced [:clock :tick])))
    (is (< 0.0 (:growth-progress advanced) 1.0))
    (is (= 1.0 (:growth-progress grown)))
    (is (= paused (model/advance-world-steps paused 30 (/ 1.0 30.0))))))

(deftest animated-glyphs-remain-deterministic
  (let [world (model/advance-world-steps (model/initial-world 42)
                                         60 (/ 1.0 30.0))
        foliage-cell (first (cells-with-role world :foliage))]
    (is foliage-cell)
    (is (= (cells/display-glyph world foliage-cell)
           (cells/display-glyph world foliage-cell)))))

(deftest seasons-change-presentation-not-tree-geometry
  (let [spring (model/initial-world 42)
        winter (model/cycle-theme spring 3)]
    (is (= (:tree spring) (:tree winter)))
    (is (not= (:cells spring) (:cells winter)))))
