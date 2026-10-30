;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.app.controller-test
  (:require
   [clojure.test :refer [deftest is]]
   [fireworks.app.controller :as controller]
   [fireworks.model.core :as model]))

(deftest keys-map-to-c-compatible-commands
  (is (= :quit (controller/key-event->command {:raw-key \q})))
  (is (= :quit (controller/key-event->command {:key-code 27})))
  (is (= :raise-sim-fps (controller/key-event->command {:raw-key \]})))
  (is (= :more-rockets (controller/key-event->command {:raw-key \+})))
  (is (= :more-rockets (controller/key-event->command {:raw-key \=})))
  (is (= :previous-theme (controller/key-event->command {:raw-key \T})))
  (is (= :restart-show (controller/key-event->command {:raw-key \r})))
  (is (nil? (controller/key-event->command {:raw-key \x}))))

(deftest commands-update-only-the-requested-setting
  (let [world (model/initial-world 7)]
    (is (= 35 (get-in (controller/apply-command world :raise-sim-fps)
                      [:clock :simulation-fps])))
    (is (= 7 (model/firework-count
              (controller/apply-command world :more-rockets))))
    (is (= 9 (:theme-index
              (controller/apply-command world :previous-theme))))
    (is (get-in (controller/apply-command world :toggle-pause)
                [:clock :paused?]))))
