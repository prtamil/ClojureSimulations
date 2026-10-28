(ns sim.model.core-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [sim.render.ascii :as ascii]
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

(defn- momentum [balls]
  [(reduce + (map #(* (:mass %) (:vx %)) balls))
   (reduce + (map #(* (:mass %) (:vy %)) balls))])

(defn- kinetic-energy [balls]
  (reduce + (map #(* 0.5 (:mass %)
                     (+ (* (:vx %) (:vx %)) (* (:vy %) (:vy %))))
                 balls)))

(deftest seeded-spawning-is-repeatable-valid-and-non-overlapping
  (let [world (model/initial-world 42)
        balls (:balls world)]
    (is (= world (model/initial-world 42)))
    (is (= (get-in world [:config :balls :count]) (count balls)))
    (is (= (count balls) (count (distinct (map :id balls)))))
    (is (not-any? (fn [[a b]] (model/balls-overlap? a b))
                  (unique-pairs balls)))
    (is (every? (fn [{:keys [x y radius]}]
                  (and (<= radius x (- 40.0 radius))
                       (<= radius y (- 20.0 radius))))
                balls))))

(deftest equal-mass-head-on-collision-swaps-velocities
  (let [a {:id 0 :x 10.0 :y 10.0 :vx 2.0 :vy 0.0
           :radius 0.5 :mass 1.0 :color 0}
        b {:id 1 :x 11.0 :y 10.0 :vx -3.0 :vy 0.0
           :radius 0.5 :mass 1.0 :color 1}
        [a' b'] (model/resolve-ball-collision 1.0 a b)]
    (is (near? -3.0 (:vx a')))
    (is (near? 2.0 (:vx b')))
    (is (near? 0.0 (:vy a')))
    (is (near? 0.0 (:vy b')))))

(deftest elastic-collision-conserves-momentum-and-energy
  (let [a {:id 0 :x 10.0 :y 10.0 :vx 3.0 :vy 1.0
           :radius 0.5 :mass 1.0 :color 0}
        b {:id 1 :x 10.9 :y 10.0 :vx -1.0 :vy -2.0
           :radius 0.5 :mass 2.0 :color 1}
        before [a b]
        after (model/resolve-ball-collision 1.0 a b)
        [before-x before-y] (momentum before)
        [after-x after-y] (momentum after)]
    (is (near? before-x after-x))
    (is (near? before-y after-y))
    (is (near? (kinetic-energy before) (kinetic-energy after)))))

(deftest separated-or-receding-balls-do-not-receive-an-impulse
  (let [a {:id 0 :x 10.0 :y 10.0 :vx -1.0 :vy 0.0
           :radius 0.5 :mass 1.0 :color 0}
        b {:id 1 :x 11.0 :y 10.0 :vx 1.0 :vy 0.0
           :radius 0.5 :mass 1.0 :color 1}
        [a' b'] (model/resolve-ball-collision 1.0 a b)]
    (is (near? (:vx a) (:vx a')))
    (is (near? (:vx b) (:vx b')))))

(deftest batched-and-sequential-fixed-steps-agree
  (let [world (model/initial-world 42)
        dt (/ 1.0 60.0)
        sequential (nth (iterate #(model/update-world % dt) world) 120)
        batched (model/update-world-steps world 120 dt)]
    (is (= (:tick sequential) (:tick batched)))
    (is (near? (:elapsed-seconds sequential) (:elapsed-seconds batched)))
    (doseq [[left right] (map vector (:balls sequential) (:balls batched))
            key [:x :y :vx :vy]]
      (is (near? (get left key) (get right key) 1.0e-8)))))

(deftest long-run-keeps-every-ball-inside-the-field
  (let [world (assoc (model/initial-world 42) :speed-mult 4.0)
        final-world (model/update-world-steps world 1200 (/ 1.0 60.0))]
    (is (= 1200 (:tick final-world)))
    (is (every? (fn [{:keys [x y radius]}]
                  (and (<= radius x (- 40.0 radius))
                       (<= radius y (- 20.0 radius))))
                (:balls final-world)))))

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

(deftest update-boundary-rejects-invalid-time
  (let [world (model/initial-world)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (model/update-world-steps world -1 0.1)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (model/update-world-steps world 1 0.0)))))
