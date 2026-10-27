(ns sim.controller-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.config :as config]
   [sim.controller :as controller]
   [sim.model :as model]
   [sim.theme :as theme]))

(deftest keyboard-translation-is-renderer-independent
  (is (= :next-pattern
         (controller/key-event->command {:key :n :raw-key \n :key-code 78})))
  (is (= :toggle-pause
         (controller/key-event->command
          {:key :space :raw-key \space :key-code 32})))
  (is (= :quit
         (controller/key-event->command {:key :esc :raw-key nil :key-code 27})))
  (is (nil? (controller/key-event->command
             {:key :unknown :raw-key \x :key-code 88}))))

(deftest cyclic-controls-wrap-in-both-directions
  (let [world (model/initial-world 1)
        patterns (get-in world [:config :views :order])
        previous-pattern (controller/apply-command world :previous-pattern)
        previous-theme (controller/apply-command world :previous-theme)]
    (is (= (last patterns) (:pattern previous-pattern)))
    (is (= :particles
           (:pattern (controller/apply-command previous-pattern :next-pattern))))
    (is (= (dec (count theme/themes)) (:theme-index previous-theme)))
    (is (= 0 (:theme-index
              (controller/apply-command previous-theme :next-theme))))))

(deftest every-theme-matches-the-model-band-count
  (is (every? #(= config/supported-band-count (count (:bands %)))
              theme/themes)))

