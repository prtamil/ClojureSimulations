(ns sim.model.core-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [sim.render.ascii :as ascii]
   [sim.model.ball :as ball]
   [sim.model.config :as config]
   [sim.model.core :as model]))

(defn- near?
  ([a b] (near? a b 1.0e-9))
  ([a b tolerance]
   (< (Math/abs (- (double a) (double b))) tolerance)))

(defn- unique-pairs [balls]
  (for [left (range (count balls))
        right (range (inc left) (count balls))]
    [(nth balls left) (nth balls right)]))

(deftest seeded-spawning-is-repeatable-valid-and-non-overlapping
  (let [world (model/initial-world 42)
        balls (:balls world)]
    (is (= world (model/initial-world 42)))
    (is (= (get-in world [:config :balls :count]) (count balls)))
    (is (= (count balls) (count (distinct (map :id balls)))))
    (is (= #{:config :seed :rng-state :clock :speed-multiplier
             :theme-index :balls}
           (set (keys world))))
    (is (not-any? (fn [[a b]] (ball/overlap? a b))
                  (unique-pairs balls)))
    (is (every? (fn [{:keys [x y radius]}]
                  (and (<= radius x (- 40.0 radius))
                       (<= radius y (- 20.0 radius))))
                balls))))

(deftest batched-and-sequential-fixed-steps-agree
  (let [world (model/initial-world 42)
        dt (/ 1.0 60.0)
        sequential (nth (iterate #(model/advance-world % dt) world) 120)
        batched (model/advance-world-steps world 120 dt)]
    (is (= (get-in sequential [:clock :tick])
           (get-in batched [:clock :tick])))
    (is (near? (get-in sequential [:clock :elapsed-seconds])
               (get-in batched [:clock :elapsed-seconds])))
    (doseq [[left right] (map vector (:balls sequential) (:balls batched))
            key [:x :y :vx :vy]]
      (is (near? (get left key) (get right key) 1.0e-8)))))

(deftest long-run-keeps-every-ball-inside-the-field
  (let [world (assoc (model/initial-world 42) :speed-multiplier 4.0)
        final-world (model/advance-world-steps world 1200 (/ 1.0 60.0))]
    (is (= 1200 (get-in final-world [:clock :tick])))
    (is (every? (fn [{:keys [x y radius]}]
                  (and (<= radius x (- 40.0 radius))
                       (<= radius y (- 20.0 radius))))
                (:balls final-world)))))

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
    (is (= (:balls world) (:balls reset)))))

(deftest custom-config-drives-spawning-and-ascii
  (let [small-config (-> config/default-config
                         (assoc-in [:grid :width] 16)
                         (assoc-in [:grid :height] 8)
                         (assoc-in [:balls :count] 4)
                         (assoc-in [:balls :radius :max] 0.55))
        world (model/initial-world small-config 7)
        lines (str/split-lines (ascii/render-string world))]
    (is (= small-config (:config world)))
    (is (= 4 (count (:balls world))))
    (is (= 9 (count lines)))
    (is (every? #(= 16 (count %)) (rest lines)))
    (is (re-find #"balls:4" (first lines)))))

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
    (is (= 876 (count output)))
    (is (= 463291060 (.hashCode output)))))
