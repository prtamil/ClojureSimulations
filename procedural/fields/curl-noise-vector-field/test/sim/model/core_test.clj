(ns sim.model.core-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [sim.model.config :as config]
   [sim.model.core :as model]
   [sim.render.ascii :as ascii]))

(deftest seeded-worlds-and-evolution-are-repeatable
  (let [world (model/initial-world 2026)
        advance #(model/update-world-steps % 120 (/ 1.0 60.0))]
    (is (= world (model/initial-world 2026)))
    (is (> (count (set (map :band (:particles world)))) 1))
    (is (= (advance world) (advance (model/initial-world 2026))))))

(deftest particle-world-advances-as-semantic-data
  (let [before (model/initial-world 7)
        after (model/update-world before)]
    (is (= 1 (:tick after)))
    (is (> (model/field-time after) (model/field-time before)))
    (is (= (get-in before [:config :particles :count])
           (count (:particles after))))
    (is (seq (:cells after)))
    (is (every? #(contains? % :intensity) (vals (:cells after))))
    (is (not (contains? after :buffers)))))

(deftest every-static-view-produces-semantic-cells
  (doseq [pattern [:vector :potential :curl-magnitude]]
    (testing (name pattern)
      (let [world (-> (model/initial-world 99)
                      (assoc :pattern pattern)
                      model/update-world)]
        (is (seq (:cells world)))
        (is (every? (fn [[position cell]]
                      (= position (:position cell)))
                    (:cells world)))
        (if (= pattern :vector)
          (is (every? :velocity (vals (:cells world))))
          (is (every? #(not (contains? % :velocity))
                      (vals (:cells world)))))))))

(deftest static-batches-advance-time-and-sample-once
  (let [world (assoc (model/initial-world 99) :pattern :curl-magnitude)
        dt (/ 1.0 60.0)
        batched (model/update-world-steps world 4 dt)]
    (is (= 4 (:tick batched)))
    (is (< (Math/abs (- (model/field-time batched)
                        (* 4 dt
                           (get-in world [:config :field :drift-rate])
                           (:drift-mult world))))
           1.0e-12))
    (is (some #(> (:intensity %)
                  (get-in world [:config :render :glow-threshold]))
              (vals (:cells batched))))))

(deftest every-pattern-consumes-the-same-fixed-step-batch
  (let [dt (/ 1.0 60.0)
        tick-count 4]
    (doseq [pattern config/pattern-order]
      (testing (name pattern)
        (let [before (assoc (model/initial-world 99) :pattern pattern)
              after (model/update-world-steps before tick-count dt)]
          (is (= tick-count (:tick after)))
          (is (< (Math/abs (- (model/field-time after)
                              (* tick-count dt
                                 (get-in before [:config :field :drift-rate])
                                 (:drift-mult before))))
                 1.0e-12)))))))

(deftest batched-particle-steps-are-sequential-composition
  (let [world (model/initial-world 77)
        dt (/ 1.0 60.0)
        sequential (nth (iterate #(model/update-world % dt) world) 4)
        batched (model/update-world-steps world 4 dt)]
    (is (= sequential batched))))

(deftest controls-stay-in-range
  (let [world (model/initial-world 1)
        faster (nth (iterate #(model/adjust-speed % 1) world) 20)
        slower (nth (iterate #(model/adjust-speed % -1) world) 20)]
    (is (= (get-in world [:config :particles :speed :max]) (:speed faster)))
    (is (= (get-in world [:config :field :drift-multiplier :max])
           (:drift-mult faster)))
    (is (= (get-in world [:config :particles :speed :min]) (:speed slower)))
    (is (= (get-in world [:config :field :drift-multiplier :min])
           (:drift-mult slower)))))

(deftest reset-preserves-controls-and-reinitializes-simulation
  (let [advanced (model/update-world-steps (model/initial-world 12)
                                           20 (/ 1.0 60.0))
        customized (assoc advanced
                          :paused? true
                          :speed 16
                          :drift-mult 4
                          :pattern :warped
                          :theme-index 3
                          :sim-fps 90)
        reset (model/reset-world customized)
        control-keys [:paused? :speed :drift-mult :pattern :theme-index :sim-fps]]
    (is (= (select-keys customized control-keys)
           (select-keys reset control-keys)))
    (is (= 0 (:tick reset)))
    (is (= 0.0 (model/field-time reset)))
    (is (= (get-in reset [:config :flash :initial])
           (:flash-intensity reset)))
    (is (not= (model/field-permutation customized)
              (model/field-permutation reset)))
    (is (empty? (:cells reset)))))

(deftest paused-worlds-remain-values-and-pattern-switches-are-immediate
  (let [paused (-> (model/initial-world 42)
                   model/update-world
                   model/toggle-paused)
        vector-world (model/cycle-pattern paused 1)]
    (is (= :vector (:pattern vector-world)))
    (is (seq (:cells vector-world)))
    (is (= vector-world
           (model/update-world-steps vector-world 10 (/ 1.0 60.0))))))

(deftest batch-boundary-rejects-invalid-time-input
  (let [world (model/initial-world 42)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (model/update-world-steps world -1 (/ 1.0 60.0))))
    (is (thrown? clojure.lang.ExceptionInfo
                 (model/update-world-steps world 1.5 (/ 1.0 60.0))))
    (is (thrown? clojure.lang.ExceptionInfo
                 (model/update-world-steps world 1 0.0)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (model/update-world-steps (assoc world :pattern :unknown)
                                           1 (/ 1.0 60.0))))))

(deftest worlds-can-use-custom-configurations
  (let [small-config (-> config/default-config
                         (assoc-in [:grid :width] 24)
                         (assoc-in [:grid :height] 12)
                         (assoc-in [:particles :count] 16))
        world (model/initial-world small-config 42)
        vector-world (model/cycle-pattern world 1)
        preview-lines (-> (ascii/render-string world)
                          str/split-lines)]
    (is (= small-config (:config world)))
    (is (= 16 (count (:particles world))))
    (is (every? (fn [{[x y] :position}]
                  (and (< x 24) (< y 12)))
                (:particles world)))
    (is (seq (:cells vector-world)))
    (is (every? :velocity (vals (:cells vector-world))))
    (is (= 13 (count preview-lines)))
    (is (every? #(= 24 (count %)) (rest preview-lines)))))

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
        world (-> (model/initial-world small-config 31415)
                  (assoc :pattern :warped))
        final-world (model/update-world-steps world 500 (/ 1.0 60.0))]
    (is (= 500 (:tick final-world)))
    (is (= 32 (count (:particles final-world))))
    (is (every? (fn [{[x y] :position
                      age :age-seconds lifetime :lifetime-seconds}]
                  (and (<= 0.0 x) (< x 36)
                       (<= 0.0 y) (< y 20)
                       (<= 0.0 age) (< age lifetime)))
                (:particles final-world)))
    (is (<= (count (:cells final-world)) (* 36 20)))))

(deftest ascii-render-is-data-driven
  (let [text (-> (model/initial-world 42)
                 model/update-world
                 (ascii/render-string {:columns 40 :rows 12}))]
    (is (re-find #"pattern:PARTICLES" text))
    (is (= 13 (count (str/split-lines text))))))

(deftest ascii-derives-arrows-from-semantic-velocities
  (let [world (-> (model/initial-world 42)
                  (model/cycle-pattern 1))
        text (ascii/render-string world {:columns 120 :rows 34})]
    (is (every? :velocity (vals (:cells world))))
    (is (some #{\> \< \^ \v \/ \\} text))))
