;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.app.controller-test
  (:require
   [clojure.test :refer [deftest is]]
   [bonsai.app.controller :as controller]
   [bonsai.model.core :as model]))

(deftest key-events-preserve-the-original-case-sensitive-controls
  (is (= :next-style (controller/key-event->command {:raw-key \n})))
  (is (= :previous-style (controller/key-event->command {:raw-key \N})))
  (is (= :previous-theme (controller/key-event->command {:raw-key \T})))
  (is (= :toggle-pause (controller/key-event->command {:key :space})))
  (is (= :quit (controller/key-event->command {:key-code 27})))
  (is (nil? (controller/key-event->command {:raw-key \x}))))

(deftest commands-transform-world-values
  (let [world (model/initial-world 42)]
    (is (:paused? (controller/apply-command world :toggle-pause)))
    (is (= :shakan
           (model/current-style-key
            (controller/apply-command world :next-style))))
    (is (zero? (:growth-progress
                (controller/apply-command world :replay-growth))))
    (is (= world (controller/apply-command world :unknown)))))
