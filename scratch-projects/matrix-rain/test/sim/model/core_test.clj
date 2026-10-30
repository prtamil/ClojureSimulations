(ns sim.model.core-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [sim.render.ascii :as ascii]
            [sim.model.config :as config]
            [sim.model.core :as model]
            [sim.model.projection :as projection]))

(defn- near? [a b]
  (< (Math/abs (- (double a) (double b))) 1.0e-10))

(deftest seeded-worlds-are-repeatable-and-well-formed
  (let [world (model/initial-world 42)
        drops (:drops world)
        charset (set (get-in world [:config :rain :charset]))]
    (is (= world (model/initial-world 42)))
    (is (= #{:config :seed :rng-state :clock :speed-multiplier
             :theme-index :drops}
           (set (keys world))))
    (is (= 15 (count drops)))
    (is (= 15 (count (distinct (map :id drops)))))
    (is (every? #(<= 5 (count (:glyphs %)) 14) drops))
    (is (every? #(every? charset (:glyphs %)) drops))
    (is (every? #(<= 0 (:column %) 39) drops))))

(deftest model-moves-streams-in-simulation-seconds
  (let [simulation-config
        (-> config/default-config
            (assoc-in [:rain :drop-count] 1)
            (assoc-in [:rain :speed :min] 6.0)
            (assoc-in [:rain :speed :max] 6.0)
            (assoc-in [:rain :spawn-depth] 1)
            (assoc-in [:rain :glyph-changes-per-second] 0.0))
        before (model/initial-world simulation-config 42)
        after (model/advance-world before 0.5)]
    (is (near? -1.0 (get-in before [:drops 0 :head-y])))
    (is (near? 2.0 (get-in after [:drops 0 :head-y])))
    (is (= (get-in before [:drops 0 :glyphs])
           (get-in after [:drops 0 :glyphs])))))

(deftest glyph-mutation-is-deterministic-model-state
  (let [simulation-config
        (-> config/default-config
            (assoc-in [:rain :drop-count] 1)
            (assoc-in [:rain :glyph-changes-per-second] 1000.0))
        before (model/initial-world simulation-config 99)
        after (model/advance-world before)]
    (is (= after (model/advance-world (model/initial-world simulation-config 99))))
    (is (not= (:rng-state before) (:rng-state after)))
    (is (= (count (get-in before [:drops 0 :glyphs]))
           (count (get-in after [:drops 0 :glyphs]))))))

(deftest expired-stream-respawns-above-the-field
  (let [before (model/initial-world 42)
        expired (assoc-in before [:drops 0 :head-y] 100.0)
        after (model/advance-world expired)
        drop (first (:drops after))]
    (is (= 0 (:id drop)))
    (is (neg? (:head-y drop)))
    (is (<= 5 (count (:glyphs drop)) 14))
    (is (not= (:rng-state expired) (:rng-state after)))))

(deftest rendering-the-same-world-is-stable
  (let [world (model/advance-world-steps (model/initial-world 42)
                                         60 (/ 1.0 60.0))]
    (is (= (ascii/render-string world) (ascii/render-string world)))
    (is (= (projection/visible-glyphs world)
           (projection/visible-glyphs world)))))

(deftest projection-clips-streams-to-semantic-visible-glyphs
  (let [world (assoc (model/initial-world 42)
                     :drops [{:id 0 :column 2 :head-y 1.9 :speed 1.0
                              :glyphs [\A \B \C]}])]
    (is (= [{:position [2 1] :depth 0 :stream-length 3 :character \A}
            {:position [2 0] :depth 1 :stream-length 3 :character \B}]
           (projection/visible-glyphs world)))))

(deftest batched-and-sequential-updates-agree
  (let [world (model/initial-world 42)
        dt (/ 1.0 60.0)
        sequential (nth (iterate #(model/advance-world % dt) world) 120)
        batched (model/advance-world-steps world 120 dt)]
    (is (= (:drops sequential) (:drops batched)))
    (is (= (:rng-state sequential) (:rng-state batched)))
    (is (= (get-in sequential [:clock :tick])
           (get-in batched [:clock :tick])))
    (is (near? (get-in sequential [:clock :elapsed-seconds])
               (get-in batched [:clock :elapsed-seconds])))))

(deftest long-run-maintains-stream-invariants
  (let [world (model/advance-world-steps (model/initial-world 42)
                                         2000 (/ 1.0 60.0))
        height (config/grid-height (:config world))]
    (is (= 2000 (get-in world [:clock :tick])))
    (is (= 15 (count (:drops world))))
    (is (every? #(<= 5 (count (:glyphs %)) 14) (:drops world)))
    (is (every? #(<= (- (:head-y %) (count (:glyphs %))) height)
                (:drops world)))))

(deftest pause-reset-and-controls-preserve-contracts
  (let [world (model/initial-world 42)
        paused (model/toggle-paused world)
        unchanged (model/advance-world-steps paused 10 0.1)
        customized (assoc unchanged :speed-multiplier 4.0 :theme-index 3)
        customized (assoc-in customized [:clock :simulation-fps] 90)
        reset (model/reset-world customized)]
    (is (= paused unchanged))
    (is (get-in reset [:clock :paused?]))
    (is (= 4.0 (:speed-multiplier reset)))
    (is (= 3 (:theme-index reset)))
    (is (= 90 (get-in reset [:clock :simulation-fps])))
    (is (= 0 (get-in reset [:clock :tick])))
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

(deftest advance-boundary-rejects-invalid-time
  (let [world (model/initial-world)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (model/advance-world-steps world -1 0.1)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (model/advance-world-steps world 1 0.0)))))

(deftest ascii-output-preserves-the-visual-contract
  (let [world (model/advance-world-steps
               (model/initial-world) 8 (/ 1.0 60.0))
        output (ascii/render-string world)]
    (is (= 877 (count output)))
    (is (= 2033868264 (.hashCode output)))))
