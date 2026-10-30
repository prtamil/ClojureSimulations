(ns sim.model.config-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [sim.model.config :as config]))

(deftest default-configuration-is-valid
  (is (= config/default-config
         (config/validate! config/default-config))))

(deftest invalid-settings-report-their-boundary
  (doseq [[path value]
          [[[:field :noise-scale] 0.0]
           [[:particles :lifetime-seconds :max] 0.5]
           [[:warp :offset] [1.0]]
           [[:views :vector :lattice] 4]
           [[:render :terminal-cell-aspect] 0.0]
           [[:render :density-thresholds :mid] 0.70]
           [[:views :order] [:vector :unknown]]
           [[:render :band-count] 5]]]
    (testing (str path)
      (is (thrown? clojure.lang.ExceptionInfo
                   (config/validate! (assoc-in config/default-config path value)))))))

(deftest validation-errors-identify-the-invalid-value
  (let [error (try
                (config/validate!
                 (assoc-in config/default-config [:field :noise-scale] 0.0))
                nil
                (catch clojure.lang.ExceptionInfo error
                  error))]
    (is (= [:field :noise-scale] (:path (ex-data error))))
    (is (= 0.0 (:value (ex-data error))))))

