(ns sim.model.config-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [sim.model.config :as config]))

(deftest default-configuration-is-valid
  (is (= config/default-config (config/validate! config/default-config)))
  (is (= 800 (config/cell-count config/default-config))))

(deftest invalid-settings-report-their-boundary
  (doseq [[path value]
          [[[:grid :width] 0]
           [[:balls :count] 0]
           [[:balls :radius :max] 50.0]
           [[:balls :speed :min] 20.0]
           [[:balls :speed-multiplier :initial] 100.0]
           [[:physics :substeps] 0]
           [[:physics :restitution] 2.0]
           [[:clock :simulation-fps :initial] 500]
           [[:render :terminal-cell-aspect] 0.0]
           [[:render :band-count] 5]]]
    (testing (str path)
      (is (thrown? clojure.lang.ExceptionInfo
                   (config/validate! (assoc-in config/default-config path value)))))))
