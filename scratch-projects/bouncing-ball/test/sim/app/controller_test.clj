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

(deftest controls-stay-in-range
  (let [world (model/initial-world)
        faster (nth (iterate #(controller/apply-command % :faster) world) 20)
        slower (nth (iterate #(controller/apply-command % :slower) world) 20)
        raised (nth (iterate #(controller/apply-command % :raise-sim-fps)
                             world)
                    100)
        lowered (nth (iterate #(controller/apply-command % :lower-sim-fps)
                              world)
                     100)]
    (is (= (get-in world [:config :ball :speed-multiplier :max])
           (:speed-mult faster)))
    (is (= (get-in world [:config :ball :speed-multiplier :min])
           (:speed-mult slower)))
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
