;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.render.lanterna-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [fireworks.model.core :as model]
   [fireworks.render.lanterna :as lanterna]))

(deftest terminal-events-use-the-shared-controller
  (is (= :quit (lanterna/key->command {:type :escape})))
  (is (= :quit (lanterna/key->command
                {:type :character :character (char 3)})))
  (is (= :more-rockets
         (lanterna/key->command {:type :character :character \+})))
  (is (nil? (lanterna/key->command {:type :key :key :arrowup}))))

(deftest terminal-status-matches-the-c-hud
  (let [world (model/initial-world)]
    (is (str/includes? (lanterna/status-text world 59.95) "60.0 fps"))
    (is (str/includes? (lanterna/status-text world 59.95) "sim:30 Hz"))
    (is (str/includes? (lanterna/status-text world 59.95) "rkt: 6"))
    (is (= " theme:matrix " (lanterna/theme-text world)))))
