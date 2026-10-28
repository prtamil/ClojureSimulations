;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.app.controller-test
  (:require
   [clojure.test :refer [deftest is]]
   [leaf-fall.app.controller :as controller]
   [leaf-fall.model.core :as model]))

(deftest key-events-map-to-domain-commands
  (is (= :quit (controller/key-event->command {:raw-key \q})))
  (is (= :quit (controller/key-event->command {:key-code 27})))
  (is (= :skip (controller/key-event->command {:key :space})))
  (is (= :new-tree (controller/key-event->command {:raw-key \r})))
  (is (= :raise-sim-fps (controller/key-event->command {:raw-key \]})))
  (is (nil? (controller/key-event->command {:raw-key \x}))))

(deftest commands-transform-the-world
  (let [world (model/initial-world 42)]
    (is (= :falling (:phase (controller/apply-command world :skip))))
    (is (= 2 (:cycle (controller/apply-command world :new-tree))))
    (is (= world (controller/apply-command world :unknown)))))
