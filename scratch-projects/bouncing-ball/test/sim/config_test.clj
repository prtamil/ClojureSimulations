(ns sim.config-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [sim.config :as config]))

(deftest default-configuration-is-valid
  (is (= config/default-config
         (config/validate! config/default-config)))
  (is (= 800 (config/cell-count config/default-config))))

(deftest invalid-settings-report-their-boundary
  (doseq [[path value]
          [[[:grid :width] 0]
           [[:ball :radius] 50.0]
           [[:ball :initial :x] -1.0]
           [[:ball :initial :speed] 0.0]
           [[:ball :speed-multiplier :initial] 100.0]
           [[:clock :simulation-fps :initial] 500]
           [[:noise :scale] 0.0]
           [[:render :background] [0 0 999]]
           [[:render :band-count] 5]]]
    (testing (str path)
      (is (thrown? clojure.lang.ExceptionInfo
                   (config/validate! (assoc-in config/default-config path value)))))))
