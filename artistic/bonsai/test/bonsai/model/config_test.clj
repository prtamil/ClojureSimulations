;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.model.config-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [bonsai.model.config :as config]))

(defn- validation-error-data [simulation-config]
  (try
    (config/validate! simulation-config)
    nil
    (catch clojure.lang.ExceptionInfo error
      (ex-data error))))

(deftest default-configuration-is-valid
  (is (= config/default-config (config/validate! config/default-config)))
  (is (= 4032 (config/cell-count config/default-config))))

(deftest invalid-configuration-identifies-its-path
  (is (= [:grid :width]
         (:path (validation-error-data
                 (assoc-in config/default-config [:grid :width] 0))))))

(deftest tree-animation-and-render-ranges-are-validated
  (doseq [[setting-path invalid-value]
          [[[:tree :cell-aspect] 0.0]
           [[:tree :branch-length-factor] 1.0]
           [[:tree :branch-band] [0.9 0.2]]
           [[:animation :rustle-rate] 1.1]
           [[:render :terminal-cell-aspect] 0.0]
           [[:render :hud-height] -1]
           [[:render :background] [0 0 300]]]]
    (testing (str setting-path)
      (is (= setting-path
             (:path (validation-error-data
                     (assoc-in config/default-config
                               setting-path invalid-value))))))))

(deftest related-limits-and-style-parameters-are-validated
  (is (= [:tree :trunk-segments]
         (:path
          (validation-error-data
           (-> config/default-config
               (assoc-in [:tree :trunk-segments-min] 20)
               (assoc-in [:tree :trunk-segments-max] 10))))))
  (is (= [:clock :simulation-fps]
         (:path
          (validation-error-data
           (assoc-in config/default-config
                     [:clock :simulation-fps :initial] 999)))))
  (with-redefs [config/styles
                (assoc-in config/styles [:moyogi :branch-density] 1.5)]
    (is (= [:styles :moyogi :branch-density]
           (:path (validation-error-data config/default-config))))))
