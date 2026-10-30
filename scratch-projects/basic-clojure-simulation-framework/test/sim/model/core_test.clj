(ns sim.model.core-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [sim.render.ascii :as ascii]
   [sim.model.config :as config]
   [sim.model.core :as model]))

(defn- near? [a b]
  (< (Math/abs (- (double a) (double b))) 1.0e-12))

(deftest empty-values-document-the-persistent-world
  (is (= {:tick 0 :elapsed-seconds 0.0
          :paused? false :simulation-fps 0}
         model/empty-clock))
  (is (= #{:config :seed :clock :theme-index :particles}
         (set (keys model/empty-world)))))

(deftest seeded-worlds-are-repeatable
  (is (= (model/initial-world 42) (model/initial-world 42)))
  (is (= 42 (:seed (model/initial-world 42))))
  (is (= 43 (:seed (model/initial-world 43)))))

(deftest world-advances-in-seconds
  (let [before (model/initial-world 42)
        after (model/advance-world-steps before 6 0.1)
        [first-particle second-particle] (:particles after)]
    (is (= 6 (get-in after [:clock :tick])))
    (is (near? 0.6 (get-in after [:clock :elapsed-seconds])))
    (is (near? 9.8 (:x first-particle)))
    (is (near? 10.0 (:y first-particle)))
    (is (near? 20.0 (:x second-particle)))
    (is (near? 8.6 (:y second-particle)))))

(deftest particles-wrap-at-grid-boundaries
  (let [world (assoc (model/initial-world 42)
                     :particles [{:id 1 :x 39.0 :y 19.0 :vx 4.0 :vy 3.0}])
        particle (-> (model/advance-world world 0.5) :particles first)]
    (is (near? 1.0 (:x particle)))
    (is (near? 0.5 (:y particle)))))

(deftest batches-match-sequential-fixed-steps
  (let [world (model/initial-world 42)
        dt (/ 1.0 60.0)
        sequential (nth (iterate #(model/advance-world % dt) world) 20)
        batched (model/advance-world-steps world 20 dt)]
    (is (= (get-in sequential [:clock :tick])
           (get-in batched [:clock :tick])))
    (is (near? (get-in sequential [:clock :elapsed-seconds])
               (get-in batched [:clock :elapsed-seconds])))
    (doseq [[left right] (map vector (:particles sequential) (:particles batched))]
      (is (near? (:x left) (:x right)))
      (is (near? (:y left) (:y right))))))

(deftest pause-and-reset-are-pure-state-transitions
  (let [world (model/initial-world 42)
        paused (model/toggle-paused world)
        unchanged (model/advance-world-steps paused 10 0.1)
        customized (-> unchanged
                       (assoc :theme-index 3)
                       (assoc-in [:clock :simulation-fps] 90))
        reset (model/reset-world customized)]
    (is (= paused unchanged))
    (is (get-in reset [:clock :paused?]))
    (is (= 90 (get-in reset [:clock :simulation-fps])))
    (is (= 3 (:theme-index reset)))
    (is (= 0 (get-in reset [:clock :tick])))
    (is (= (:particles world) (:particles reset)))))

(deftest custom-config-drives-model-and-ascii
  (let [small-config
        (-> config/default-config
            (assoc-in [:grid :width] 12)
            (assoc-in [:grid :height] 6)
            (assoc-in [:particles :initial]
                      [{:id 1 :x 2.0 :y 3.0 :vx 1.0 :vy 0.0}]))
        world (model/initial-world small-config 7)
        lines (str/split-lines (ascii/render-string world))]
    (is (= small-config (:config world)))
    (is (re-find #"theme:DEFAULT" (first lines)))
    (is (= 7 (count lines)))
    (is (every? #(= 12 (count %)) (rest lines)))))

(deftest update-boundary-rejects-invalid-time
  (let [world (model/initial-world)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (model/advance-world-steps world -1 0.1)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (model/advance-world-steps world 1 0.0)))))
