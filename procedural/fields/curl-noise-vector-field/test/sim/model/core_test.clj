(ns sim.model.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [sim.model.config :as config]
   [sim.model.core :as model]))

(defn- select-view [world pattern]
  (loop [world world]
    (if (= pattern (get-in world [:view :pattern]))
      world
      (recur (model/cycle-view world 1)))))

(deftest empty-values-document-the-complete-world
  (is (= {:tick 0 :paused? false :simulation-fps 0}
         model/empty-clock))
  (is (= {:pattern :particles :samples {}} model/empty-view))
  (is (= {:time 0.0 :permutation []} (:field model/empty-world)))
  (is (= {} (:trail model/empty-world)))
  (is (= #{:config :random-state :clock :field :particles :trail :view
           :particle-speed :drift-multiplier :theme-index :flash-intensity}
         (set (keys model/empty-world)))))

(deftest seeded-worlds-and-evolution-are-repeatable
  (let [world (model/initial-world 2026)
        advance #(model/advance-world-steps % 120 (/ 1.0 60.0))]
    (is (= world (model/initial-world 2026)))
    (is (> (count (set (map :band (:particles world)))) 1))
    (is (= (advance world) (advance (model/initial-world 2026))))))

(deftest particle-world-advances-as-domain-data
  (let [before (model/initial-world 7)
        after (model/advance-world before)]
    (is (= 1 (get-in after [:clock :tick])))
    (is (> (model/field-time after) (model/field-time before)))
    (is (= (get-in before [:config :particles :count])
           (count (:particles after))))
    (is (seq (:trail after)))
    (is (every? #(contains? % :intensity) (vals (:trail after))))
    (is (empty? (get-in after [:view :samples])))
    (is (not (contains? after :cells)))))

(deftest particles-move-age-and-leave-trail-points
  (let [particle {:position [60.0 34.0]
                  :band 1
                  :age-seconds 0.0
                  :lifetime-seconds 10.0}
        before (assoc (model/initial-world 9)
                      :particles [particle]
                      :trail {})
        before-particle (first (:particles before))
        after (model/advance-world before 0.25)
        after-particle (first (:particles after))]
    (is (= 0.25 (:age-seconds after-particle)))
    (is (not= (:position before-particle) (:position after-particle)))
    (is (seq (:trail after)))
    (is (every? #(= #{:position :intensity :band} (set (keys %)))
                (vals (:trail after))))))

(deftest trail-persists-and-fades-after-particles-are-gone
  (let [trail {[2 3] {:position [2 3] :intensity 1.0 :band 1}}
        world (assoc (model/initial-world 42)
                     :particles []
                     :trail trail)
        faded (model/advance-world world 1.0)
        gone (model/advance-world world 100.0)]
    (is (< (:intensity (get (:trail faded) [2 3])) 1.0))
    (is (empty? (:trail gone)))))

(deftest every-static-view-calculates-field-samples
  (doseq [pattern [:vector :potential :curl-magnitude]]
    (testing (name pattern)
      (let [world (-> (model/initial-world 99)
                      (select-view pattern)
                      model/advance-world)
            samples (get-in world [:view :samples])]
        (is (seq samples))
        (is (every? (fn [[position sample]]
                      (= position (:position sample)))
                    samples))
        (if (= pattern :vector)
          (is (every? :velocity (vals samples)))
          (is (every? #(not (contains? % :velocity)) (vals samples))))))))

(deftest static-batches-advance-time-and-sample-once
  (let [world (select-view (model/initial-world 99) :curl-magnitude)
        dt (/ 1.0 60.0)
        batched (model/advance-world-steps world 4 dt)]
    (is (= 4 (get-in batched [:clock :tick])))
    (is (< (Math/abs (- (model/field-time batched)
                        (* 4 dt
                           (get-in world [:config :field :drift-rate])
                           (:drift-multiplier world))))
           1.0e-12))
    (is (some #(> (:intensity %)
                  (get-in world [:config :render :glow-threshold]))
              (vals (get-in batched [:view :samples]))))))

(deftest every-view-consumes-the-same-fixed-step-batch
  (let [dt (/ 1.0 60.0)
        tick-count 4]
    (doseq [pattern config/pattern-order]
      (testing (name pattern)
        (let [before (select-view (model/initial-world 99) pattern)
              after (model/advance-world-steps before tick-count dt)]
          (is (= tick-count (get-in after [:clock :tick])))
          (is (< (Math/abs (- (model/field-time after)
                              (* tick-count dt
                                 (get-in before [:config :field :drift-rate])
                                 (:drift-multiplier before))))
                 1.0e-12)))))))

(deftest batched-particle-steps-are-sequential-composition
  (let [world (model/initial-world 77)
        dt (/ 1.0 60.0)
        sequential (nth (iterate #(model/advance-world % dt) world) 4)
        batched (model/advance-world-steps world 4 dt)]
    (is (= sequential batched))))

(deftest controls-stay-in-range
  (let [world (model/initial-world 1)
        faster (nth (iterate #(model/change-flow-speed % 1) world) 20)
        slower (nth (iterate #(model/change-flow-speed % -1) world) 20)]
    (is (= (get-in world [:config :particles :speed :max])
           (:particle-speed faster)))
    (is (= (get-in world [:config :field :drift-multiplier :max])
           (:drift-multiplier faster)))
    (is (= (get-in world [:config :particles :speed :min])
           (:particle-speed slower)))
    (is (= (get-in world [:config :field :drift-multiplier :min])
           (:drift-multiplier slower)))))

(deftest reset-preserves-controls-and-reinitializes-simulation
  (let [advanced (model/advance-world-steps (model/initial-world 12)
                                            20 (/ 1.0 60.0))
        customized (-> advanced
                       (assoc :particle-speed 16
                              :drift-multiplier 4
                              :theme-index 3)
                       (assoc-in [:clock :paused?] true)
                       (assoc-in [:clock :simulation-fps] 90)
                       (assoc-in [:view :pattern] :warped))
        reset (model/reset-world customized)
        control-paths [[:clock :paused?]
                       [:clock :simulation-fps]
                       [:view :pattern]
                       [:particle-speed]
                       [:drift-multiplier]
                       [:theme-index]]]
    (doseq [path control-paths]
      (is (= (get-in customized path) (get-in reset path))))
    (is (= 0 (get-in reset [:clock :tick])))
    (is (= 0.0 (model/field-time reset)))
    (is (= (get-in reset [:config :flash :initial])
           (:flash-intensity reset)))
    (is (not= (model/field-permutation customized)
              (model/field-permutation reset)))
    (is (empty? (:trail reset)))))

(deftest paused-worlds-remain-values-and-view-switches-are-immediate
  (let [paused (-> (model/initial-world 42)
                   model/advance-world
                   model/toggle-paused)
        vector-world (model/cycle-view paused 1)]
    (is (= :vector (get-in vector-world [:view :pattern])))
    (is (seq (get-in vector-world [:view :samples])))
    (is (empty? (:trail vector-world)))
    (is (= vector-world
           (model/advance-world-steps vector-world 10 (/ 1.0 60.0))))))

(deftest batch-boundary-rejects-invalid-time-input
  (let [world (model/initial-world 42)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (model/advance-world-steps world -1 (/ 1.0 60.0))))
    (is (thrown? clojure.lang.ExceptionInfo
                 (model/advance-world-steps world 1.5 (/ 1.0 60.0))))
    (is (thrown? clojure.lang.ExceptionInfo
                 (model/advance-world-steps world 1 0.0)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (model/advance-world-steps
                  (assoc-in world [:view :pattern] :unknown)
                  1 (/ 1.0 60.0))))))

(deftest worlds-can-use-custom-configurations
  (let [small-config (-> config/default-config
                         (assoc-in [:grid :width] 24)
                         (assoc-in [:grid :height] 12)
                         (assoc-in [:particles :count] 16))
        world (model/initial-world small-config 42)
        vector-world (model/cycle-view world 1)]
    (is (= small-config (:config world)))
    (is (= 16 (count (:particles world))))
    (is (every? (fn [{[x y] :position}]
                  (and (< x 24) (< y 12)))
                (:particles world)))
    (is (seq (get-in vector-world [:view :samples])))
    (is (every? :velocity (vals (get-in vector-world [:view :samples]))))))

(deftest invalid-configurations-fail-at-the-boundary
  (let [invalid-config (assoc-in config/default-config [:grid :width] 0)]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"\[:grid :width\]"
                          (model/initial-world invalid-config 42)))))

(deftest particles-remain-valid-over-a-long-run
  (let [small-config (-> config/default-config
                         (assoc-in [:grid :width] 36)
                         (assoc-in [:grid :height] 20)
                         (assoc-in [:particles :count] 32))
        world (select-view (model/initial-world small-config 31415) :warped)
        final-world (model/advance-world-steps world 500 (/ 1.0 60.0))]
    (is (= 500 (get-in final-world [:clock :tick])))
    (is (= 32 (count (:particles final-world))))
    (is (every? (fn [{[x y] :position
                      age :age-seconds lifetime :lifetime-seconds}]
                  (and (<= 0.0 x) (< x 36)
                       (<= 0.0 y) (< y 20)
                       (<= 0.0 age) (< age lifetime)))
                (:particles final-world)))
    (is (<= (count (:trail final-world)) (* 36 20)))))
