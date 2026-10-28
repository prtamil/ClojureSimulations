(ns sim.app.controller-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.app.controller :as controller]
   [sim.model.core :as model]
   [sim.utility.theme :as theme]))

(deftest keyboard-translation-is-renderer-independent
  (is (= :toggle-pause
         (controller/key-event->command
          {:key :space :raw-key \space :key-code 32})))
  (is (= :reset
         (controller/key-event->command
          {:key :r :raw-key \r :key-code 82})))
  (is (= :next-theme
         (controller/key-event->command
          {:key :t :raw-key \t :key-code 84})))
  (is (= :previous-theme
         (controller/key-event->command
          {:key :t :raw-key \T :key-code 84})))
  (is (= :quit
         (controller/key-event->command
          {:key :esc :raw-key nil :key-code 27})))
  (is (nil? (controller/key-event->command
             {:key :unknown :raw-key \x :key-code 88}))))

(deftest simulation-rate-controls-stay-in-range
  (let [world (model/initial-world)
        raised (nth (iterate #(controller/apply-command % :raise-sim-fps)
                             world)
                    100)
        lowered (nth (iterate #(controller/apply-command % :lower-sim-fps)
                              world)
                     100)]
    (is (= (get-in world [:config :clock :simulation-fps :max])
           (:sim-fps raised)))
    (is (= (get-in world [:config :clock :simulation-fps :min])
           (:sim-fps lowered)))))

(deftest themes-wrap-in-both-directions
  (let [world (model/initial-world)
        previous (controller/apply-command world :previous-theme)]
    (is (= (dec (count theme/themes)) (:theme-index previous)))
    (is (= 0 (:theme-index
              (controller/apply-command previous :next-theme))))))
