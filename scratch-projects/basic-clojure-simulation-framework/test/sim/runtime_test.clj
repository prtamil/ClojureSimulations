(ns sim.runtime-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.runtime :as runtime]))

(deftest gui-lifecycle-can-be-claimed-again-after-close
  (try
    (runtime/release-gui!)
    (is (true? (runtime/claim-gui!)))
    (is (false? (runtime/claim-gui!)))
    (runtime/release-gui!)
    (is (true? (runtime/claim-gui!)))
    (finally
      (runtime/release-gui!))))
