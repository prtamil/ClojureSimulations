(ns sim.model.drop-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.model.config :as config]
   [sim.model.drop :as drop]))

(deftest seeded-stream-creation-is-repeatable
  (let [[state drops] (drop/spawn-many config/default-config 42)
        [same-state same-drops] (drop/spawn-many config/default-config 42)]
    (is (= state same-state))
    (is (= drops same-drops))
    (is (= 15 (count drops)))
    (is (every? #(<= 5 (count (:glyphs %)) 14) drops))))

(deftest motion-is-time-based
  (let [simulation-config (assoc-in config/default-config
                                    [:rain :glyph-changes-per-second] 0.0)
        [_ stream] (drop/spawn simulation-config 7 0)
        [state moved] (drop/advance simulation-config 2.0 0.25 99 stream)]
    (is (= 99 state))
    (is (= (+ (:head-y stream) (* 2.0 (:speed stream) 0.25))
           (:head-y moved)))
    (is (= (:glyphs stream) (:glyphs moved)))))

(deftest mutation-rate-is-expressed-per-second
  (let [simulation-config (assoc-in config/default-config
                                    [:rain :glyph-changes-per-second] 1000.0)
        [_ stream] (drop/spawn simulation-config 7 0)
        [state moved] (drop/advance simulation-config 1.0 0.1 99 stream)]
    (is (not= 99 state))
    (is (= (count (:glyphs stream)) (count (:glyphs moved))))))
