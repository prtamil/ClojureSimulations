(ns sim.model-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [sim.ascii :as ascii]
   [sim.config :as config]
   [sim.model :as model]))

(deftest seeded-worlds-are-repeatable
  (let [world (model/initial-world 2026)]
    (is (= world (model/initial-world 2026)))
    (is (> (count (set (map :color (:particles world)))) 1))))

(deftest seeded-evolution-is-repeatable
  (let [advance #(nth (iterate model/update-world %) 120)]
    (is (= (advance (model/initial-world 2026))
           (advance (model/initial-world 2026))))))

(deftest particle-world-advances
  (let [before (model/initial-world 7)
        after (model/update-world before)]
    (is (= 1 (:tick after)))
    (is (> (:field-time after) (:field-time before)))
    (is (= (get-in before [:config :particles :count])
           (count (:particles after))))
    (is (some #(> % (get-in before [:config :render :glow-threshold]))
              (get-in after [:buffers :glow])))))

(deftest every-static-view-produces-visible-data
  (doseq [pattern [:vector :potential :curl-magnitude]]
    (testing (name pattern)
      (let [world (-> (model/initial-world 99)
                      (assoc :pattern pattern)
                      model/update-world)]
        (is (some #(> % (get-in world [:config :render :glow-threshold]))
                  (get-in world [:buffers :glow])))))))

(deftest batched-static-steps-update-time-and-render-once
  (let [world (assoc (model/initial-world 99) :pattern :curl-magnitude)
        dt (/ 1.0 60.0)
        batched (model/update-world-steps world 4 dt)]
    (is (= 4 (:tick batched)))
    (is (< (Math/abs (- (:field-time batched)
                        (* 4 dt
                           (get-in world [:config :field :drift-rate])
                           (:drift-mult world))))
           1.0e-12))
    (is (some #(> % (get-in world [:config :render :glow-threshold]))
              (get-in batched [:buffers :glow])))))

(deftest every-pattern-consumes-the-same-fixed-step-batch
  (let [dt (/ 1.0 60.0)
        tick-count 4]
    (doseq [pattern config/pattern-order]
      (testing (name pattern)
        (let [before (assoc (model/initial-world 99) :pattern pattern)
              after (model/update-world-steps before tick-count dt)]
          (is (= tick-count (:tick after)))
          (is (= pattern (:buffer-pattern after)))
          (is (< (Math/abs (- (:field-time after)
                              (* tick-count dt
                                 (get-in before [:config :field :drift-rate])
                                 (:drift-mult before))))
                 1.0e-12)))))))

(deftest batched-particle-steps-preserve-per-tick-integration
  (let [world (model/initial-world 77)
        dt (/ 1.0 60.0)
        sequential (nth (iterate #(model/update-world % dt) world) 4)
        batched (model/update-world-steps world 4 dt)
        sequential-glow (get-in sequential [:buffers :glow])
        batched-glow (get-in batched [:buffers :glow])
        max-glow-difference (apply max
                                   (map #(Math/abs (- %1 %2))
                                        sequential-glow
                                        batched-glow))]
    (is (= (:particles sequential) (:particles batched)))
    (is (= (:rng-state sequential) (:rng-state batched)))
    (is (= (select-keys sequential [:tick :field-time :supernova])
           (select-keys batched [:tick :field-time :supernova])))
    (is (= (get-in sequential [:buffers :color])
           (get-in batched [:buffers :color])))
    (is (< max-glow-difference 1.0e-12))))

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
  (let [advanced (nth (iterate model/update-world (model/initial-world 12)) 20)
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
    (is (= 0.0 (:field-time reset)))
    (is (= (get-in reset [:config :flash :initial]) (:supernova reset)))
    (is (nil? (:buffer-pattern reset)))
    (is (not= (:noise-permutation customized) (:noise-permutation reset)))
    (is (every? zero? (get-in reset [:buffers :glow])))))

(deftest paused-pattern-switch-refreshes-cache-without-advancing-time
  (let [dt (/ 1.0 60.0)
        paused (-> (model/initial-world 42)
                   model/update-world
                   (assoc :paused? true)
                   (model/cycle-pattern 1))
        refreshed (model/update-world-steps paused 1 dt)]
    (is (= :vector (:pattern refreshed)))
    (is (= :vector (:buffer-pattern refreshed)))
    (is (seq (get-in refreshed [:buffers :vectors])))
    (is (= (select-keys paused [:tick :field-time :supernova :particles :rng-state])
           (select-keys refreshed [:tick :field-time :supernova :particles :rng-state])))))

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
        vector-world (-> world
                         (assoc :pattern :vector)
                         model/update-world)
        preview-lines (-> (ascii/render-string world)
                          str/split-lines)]
    (is (= small-config (:config world)))
    (is (= 16 (count (:particles world))))
    (is (= (* 24 12) (count (get-in world [:buffers :glow]))))
    (is (every? #(< (:x %) 24) (:particles world)))
    (is (every? #(< (:y %) 12) (:particles world)))
    (is (= (* 24 12) (count (get-in vector-world [:buffers :glow]))))
    (is (seq (get-in vector-world [:buffers :vectors])))
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
        final-world (nth (iterate model/update-world world) 500)]
    (is (= 500 (:tick final-world)))
    (is (= 32 (count (:particles final-world))))
    (is (every? (fn [{:keys [x y age max-age]}]
                  (and (<= 0.0 x) (< x 36)
                       (<= 0.0 y) (< y 20)
                       (<= 0 age) (< age max-age)))
                (:particles final-world)))
    (is (= (* 36 20) (count (get-in final-world [:buffers :glow]))))))

(deftest ascii-render-is-data-driven
  (let [text (-> (model/initial-world 42)
                 model/update-world
                 (ascii/render-string {:columns 40 :rows 12}))]
    (is (re-find #"pattern:PARTICLES" text))
    (is (= 13 (count (str/split-lines text))))))

(deftest ascii-derives-arrows-from-model-vectors
  (let [world (-> (model/initial-world 42)
                  (assoc :pattern :vector)
                  model/update-world)
        text (ascii/render-string world {:columns 120 :rows 34})]
    (is (seq (get-in world [:buffers :vectors])))
    (is (not (contains? (:buffers world) :glyph)))
    (is (some #{\> \< \^ \v \/ \\} text))))


