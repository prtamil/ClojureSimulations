;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.model.config-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [fireworks.model.config :as config]))

(deftest default-configuration-is-valid
  (is (= config/default-config (config/validate! config/default-config)))
  (is (= 20 (config/rocket-limit config/default-config))))

(deftest invalid-values-report-their-path
  (testing "grid dimensions"
    (let [error (try
                  (config/validate! (assoc-in config/default-config
                                              [:grid :width] 3))
                  nil
                  (catch clojure.lang.ExceptionInfo error error))]
      (is (= [:grid :width] (:path (ex-data error))))))
  (testing "ordered ranges"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"rocket-count range"
         (config/validate! (assoc-in config/default-config
                                     [:rockets :count :initial] 21))))))
