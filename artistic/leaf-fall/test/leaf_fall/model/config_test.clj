;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.model.config-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [leaf-fall.model.config :as config]))

(deftest default-configuration-is-valid
  (is (= config/default-config (config/validate! config/default-config)))
  (is (= 96 (config/grid-width config/default-config)))
  (is (= 42 (config/grid-height config/default-config))))

(deftest invalid-settings-report-their-path
  (testing "grid bounds"
    (let [error (try
                  (config/validate! (assoc-in config/default-config
                                              [:grid :width] 12))
                  nil
                  (catch clojure.lang.ExceptionInfo error error))]
      (is (= [:grid :width] (:path (ex-data error))))))
  (testing "ordered trunk fractions"
    (is (thrown? clojure.lang.ExceptionInfo
                 (config/validate!
                  (assoc-in config/default-config
                            [:tree :trunk-height-min] 0.9)))))
  (testing "ordered clock range"
    (is (thrown? clojure.lang.ExceptionInfo
                 (config/validate!
                  (assoc-in config/default-config
                            [:clock :simulation-fps :initial] 500)))))
  (testing "terminal cell aspect"
    (is (thrown? clojure.lang.ExceptionInfo
                 (config/validate!
                  (assoc-in config/default-config
                            [:render :terminal-cell-aspect] 0.0)))))
  (testing "fall speeds must form a non-empty positive vector"
    (is (thrown? clojure.lang.ExceptionInfo
                 (config/validate!
                  (assoc-in config/default-config
                            [:fall :speed-options] []))))))
