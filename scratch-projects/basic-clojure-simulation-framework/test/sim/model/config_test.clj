(ns sim.model.config-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [sim.model.config :as config]))

(deftest default-configuration-is-valid
  (is (= config/default-config
         (config/validate! config/default-config)))
  (is (= 800 (config/cell-count config/default-config))))

(deftest invalid-settings-report-their-boundary
  (doseq [[path value]
          [[[:grid :width] 0]
           [[:clock :simulation-fps :initial] 500]
           [[:render :terminal-cell-aspect] 0.0]
           [[:render :background] [0 0 999]]
           [[:render :band-count] 5]
           [[:particles :initial]
            [{:id 1 :x 100.0 :y 2.0 :vx 1.0 :vy 0.0}]]]]
    (testing (str path)
      (is (thrown? clojure.lang.ExceptionInfo
                   (config/validate! (assoc-in config/default-config path value)))))))
