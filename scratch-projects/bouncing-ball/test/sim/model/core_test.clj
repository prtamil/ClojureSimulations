(ns sim.model.core-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [sim.render.ascii :as ascii]
   [sim.model.config :as config]
   [sim.model.core :as model]))

(defn- near?
  ([a b] (near? a b 1.0e-10))
  ([a b tolerance]
   (< (Math/abs (- (double a) (double b))) tolerance)))

(deftest seeded-worlds-are-repeatable
  (let [world (model/initial-world 42)]
    (is (= world (model/initial-world 42)))
    (is (= 42 (:seed world)))
    (is (= #{:config :seed :clock :speed-multiplier :theme-index :ball}
           (set (keys world))))
    (is (= #{:tick :elapsed-seconds :paused? :simulation-fps}
           (set (keys (:clock world)))))))

(deftest world-advances-in-simulation-seconds
  (let [before (model/initial-world 42)
        after (model/advance-world before 0.5)
        velocity (:ball before)]
    (is (= 1 (get-in after [:clock :tick])))
    (is (near? 0.5 (get-in after [:clock :elapsed-seconds])))
    (is (near? (+ 10.0 (* 0.5 (:vx velocity))) (get-in after [:ball :x])))
    (is (near? (+ 5.0 (* 0.5 (:vy velocity))) (get-in after [:ball :y])))))

(deftest right-wall-collision-consumes-overshoot
  (let [world (assoc (model/initial-world 42)
                     :ball {:x 39.4 :y 10.0
                            :vx 12.0 :vy 0.0 :radius 0.45})
        ball (:ball (model/advance-world world 0.1))]
    (is (near? 38.5 (:x ball)))
    (is (near? 10.0 (:y ball)))
    (is (near? -12.0 (:vx ball)))
    (is (near? 0.0 (:vy ball)))))

(deftest corner-collision-reflects-both-components
  (let [world (assoc (model/initial-world 42)
                     :ball {:x 39.4 :y 19.4
                            :vx 12.0 :vy 12.0 :radius 0.45})
        ball (:ball (model/advance-world world 0.1))]
    (is (neg? (:vx ball)))
    (is (neg? (:vy ball)))
    (is (<= (:radius ball) (:x ball) (- 40.0 (:radius ball))))
    (is (<= (:radius ball) (:y ball) (- 20.0 (:radius ball))))))

(deftest batched-and-sequential-steps-agree
  (let [world (model/initial-world 42)
        dt (/ 1.0 60.0)
        sequential (nth (iterate #(model/advance-world % dt) world) 240)
        batched (model/advance-world-steps world 240 dt)]
    (is (= (get-in sequential [:clock :tick])
           (get-in batched [:clock :tick])))
    (is (near? (get-in sequential [:clock :elapsed-seconds])
               (get-in batched [:clock :elapsed-seconds])))
    (doseq [key [:x :y :vx :vy]]
      (is (near? (get-in sequential [:ball key])
                 (get-in batched [:ball key])
                 1.0e-9)))))

(deftest ball-remains-inside-radius-aware-bounds
  (let [world (-> (model/initial-world 42)
                  (assoc :speed-multiplier 8.0))
        final-world (model/advance-world-steps world 10000 (/ 1.0 60.0))
        {:keys [x y radius]} (:ball final-world)]
    (is (= 10000 (get-in final-world [:clock :tick])))
    (is (<= radius x (- 40.0 radius)))
    (is (<= radius y (- 20.0 radius)))))

(deftest render-projection-does-not-advance-simulation-truth
  (let [world (model/initial-world 42)
        projected (model/project-world world 0.01)]
    (is (= (dissoc world :ball) (dissoc projected :ball)))
    (is (not= (:ball world) (:ball projected)))
    (is (= world (model/project-world world 0.0)))
    (is (= (model/toggle-paused world)
           (model/project-world (model/toggle-paused world) 0.01)))))

(deftest pause-reset-and-controls-are-state-transitions
  (let [world (model/initial-world 42)
        paused (model/toggle-paused world)
        unchanged (model/advance-world-steps paused 10 0.1)
        customized (assoc unchanged
                          :speed-multiplier 4.0
                          :theme-index 3)
        customized (assoc-in customized [:clock :simulation-fps] 90)
        reset (model/reset-world customized)]
    (is (= paused unchanged))
    (is (get-in reset [:clock :paused?]))
    (is (= 4.0 (:speed-multiplier reset)))
    (is (= 3 (:theme-index reset)))
    (is (= 90 (get-in reset [:clock :simulation-fps])))
    (is (= 0 (get-in reset [:clock :tick])))
    (is (= (:ball world) (:ball reset)))))

(deftest custom-config-drives-model-and-ascii
  (let [small-config
        (-> config/default-config
            (assoc-in [:grid :width] 12)
            (assoc-in [:grid :height] 6)
            (assoc-in [:ball :initial :x] 2.0)
            (assoc-in [:ball :initial :y] 3.0)
            (assoc-in [:ball :initial :speed] 4.0))
        world (model/initial-world small-config 7)
        lines (str/split-lines (ascii/render-string world))]
    (is (= small-config (:config world)))
    (is (re-find #"theme:DEFAULT" (first lines)))
    (is (= 7 (count lines)))
    (is (every? #(= 12 (count %)) (rest lines)))
    (is (= 1 (count (filter #{\O} (apply str (rest lines))))))))

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
    (is (= 901 (count output)))
    (is (= -60579741 (.hashCode output)))))
