(ns sim.model-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [sim.ascii :as ascii]
            [sim.config :as config]
            [sim.model :as model]))

(defn- near? [a b]
  (< (Math/abs (- (double a) (double b))) 1.0e-10))

(deftest seeded-worlds-are-repeatable-and-well-formed
  (let [world (model/initial-world 42)
        drops (:drops world)
        charset (set (get-in world [:config :rain :charset]))]
    (is (= world (model/initial-world 42)))
    (is (= 15 (count drops)))
    (is (= 15 (count (distinct (map :id drops)))))
    (is (every? #(= (:length %) (count (:glyphs %))) drops))
    (is (every? #(every? charset (:glyphs %)) drops))
    (is (every? #(<= 0 (:column %) 39) drops))))

(deftest model-moves-streams-in-simulation-seconds
  (let [simulation-config
        (-> config/default-config
            (assoc-in [:rain :drop-count] 1)
            (assoc-in [:rain :speed :min] 6.0)
            (assoc-in [:rain :speed :max] 6.0)
            (assoc-in [:rain :spawn-depth] 1)
            (assoc-in [:rain :glyph-change-probability] 0.0))
        before (model/initial-world simulation-config 42)
        after (model/update-world before 0.5)]
    (is (near? -1.0 (get-in before [:drops 0 :head-y])))
    (is (near? 2.0 (get-in after [:drops 0 :head-y])))
    (is (= (get-in before [:drops 0 :glyphs])
           (get-in after [:drops 0 :glyphs])))))

(deftest glyph-mutation-is-deterministic-model-state
  (let [simulation-config
        (-> config/default-config
            (assoc-in [:rain :drop-count] 1)
            (assoc-in [:rain :glyph-change-probability] 1.0))
        before (model/initial-world simulation-config 99)
        after (model/update-world before)]
    (is (= after (model/update-world (model/initial-world simulation-config 99))))
    (is (not= (:rng-state before) (:rng-state after)))
    (is (= (get-in after [:drops 0 :length])
           (count (get-in after [:drops 0 :glyphs]))))))

(deftest expired-stream-respawns-above-the-field
  (let [before (model/initial-world 42)
        expired (assoc-in before [:drops 0 :head-y] 100.0)
        after (model/update-world expired)
        drop (first (:drops after))]
    (is (= 0 (:id drop)))
    (is (neg? (:head-y drop)))
    (is (= (:length drop) (count (:glyphs drop))))
    (is (not= (:rng-state expired) (:rng-state after)))))

(deftest rendering-the-same-world-is-stable
  (let [world (model/update-world-steps (model/initial-world 42)
                                        60 (/ 1.0 60.0))]
    (is (= (ascii/render-string world) (ascii/render-string world)))
    (is (= (ascii/visible-cells world) (ascii/visible-cells world)))))

(deftest batched-and-sequential-updates-agree
  (let [world (model/initial-world 42)
        dt (/ 1.0 60.0)
        sequential (nth (iterate #(model/update-world % dt) world) 120)
        batched (model/update-world-steps world 120 dt)]
    (is (= (:drops sequential) (:drops batched)))
    (is (= (:rng-state sequential) (:rng-state batched)))
    (is (= (:tick sequential) (:tick batched)))
    (is (near? (:elapsed-seconds sequential) (:elapsed-seconds batched)))))

(deftest long-run-maintains-stream-invariants
  (let [world (model/update-world-steps (model/initial-world 42)
                                        2000 (/ 1.0 60.0))
        height (config/grid-height (:config world))]
    (is (= 2000 (:tick world)))
    (is (= 15 (count (:drops world))))
    (is (every? #(= (:length %) (count (:glyphs %))) (:drops world)))
    (is (every? #(<= (- (:head-y %) (:length %)) height) (:drops world)))))

(deftest pause-reset-and-controls-preserve-contracts
  (let [world (model/initial-world 42)
        paused (model/toggle-paused world)
        unchanged (model/update-world-steps paused 10 0.1)
        customized (assoc unchanged
                          :speed-mult 4.0 :theme-index 3 :sim-fps 90)
        reset (model/reset-world customized)]
    (is (= paused unchanged))
    (is (:paused? reset))
    (is (= 4.0 (:speed-mult reset)))
    (is (= 3 (:theme-index reset)))
    (is (= 90 (:sim-fps reset)))
    (is (= 0 (:tick reset)))
    (is (= (:drops world) (:drops reset)))))

(deftest custom-config-drives-model-and-ascii
  (let [small-config (-> config/default-config
                         (assoc-in [:grid :width] 16)
                         (assoc-in [:grid :height] 8)
                         (assoc-in [:rain :drop-count] 4)
                         (assoc-in [:rain :length :max] 8))
        world (model/initial-world small-config 7)
        lines (str/split-lines (ascii/render-string world))]
    (is (= small-config (:config world)))
    (is (= 4 (count (:drops world))))
    (is (= 9 (count lines)))
    (is (every? #(= 16 (count %)) (rest lines)))
    (is (re-find #"streams:4" (first lines)))))

(deftest update-boundary-rejects-invalid-time
  (let [world (model/initial-world)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (model/update-world-steps world -1 0.1)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (model/update-world-steps world 1 0.0)))))
