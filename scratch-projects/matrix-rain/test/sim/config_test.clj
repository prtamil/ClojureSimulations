(ns sim.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [sim.config :as config]))

(deftest default-configuration-is-valid
  (is (= config/default-config (config/validate! config/default-config)))
  (is (= 800 (config/cell-count config/default-config))))

(deftest invalid-settings-report-their-boundary
  (doseq [[path value]
          [[[:grid :width] 0]
           [[:rain :drop-count] 0]
           [[:rain :charset] ""]
           [[:rain :length :min] 20]
           [[:rain :speed :min] 30.0]
           [[:rain :speed-multiplier :initial] 100.0]
           [[:rain :glyph-change-probability] 2.0]
           [[:clock :simulation-fps :initial] 500]
           [[:render :band-count] 5]]]
    (testing (str path)
      (is (thrown? clojure.lang.ExceptionInfo
                   (config/validate! (assoc-in config/default-config path value)))))))
