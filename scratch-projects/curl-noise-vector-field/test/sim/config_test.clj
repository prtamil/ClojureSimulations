(ns sim.config-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [sim.config :as config]))

(deftest default-configuration-is-valid
  (is (= config/default-config
         (config/validate! config/default-config))))

(deftest invalid-settings-report-their-boundary
  (doseq [[path value]
          [[[:field :noise-scale] 0.0]
           [[:particles :lifetime :max-ticks] 60]
           [[:warp :offset] [1.0]]
           [[:render :density-thresholds :mid] 0.70]
           [[:views :order] [:vector :unknown]]
           [[:render :band-count] 5]]]
    (testing (str path)
      (is (thrown? clojure.lang.ExceptionInfo
                   (config/validate! (assoc-in config/default-config path value)))))))

