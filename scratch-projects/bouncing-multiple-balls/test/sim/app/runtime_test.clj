(ns sim.app.runtime-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.app.runtime :as runtime]))

(deftest gui-lifecycle-can-be-claimed-again-after-close
  (try
    (runtime/release-gui!)
    (is (true? (runtime/claim-gui!)))
    (is (false? (runtime/claim-gui!)))
    (runtime/release-gui!)
    (is (true? (runtime/claim-gui!)))
    (finally (runtime/release-gui!))))

(deftest terminal-lifecycle-can-be-claimed-again-after-close
  (try
    (runtime/release-terminal!)
    (is (true? (runtime/claim-terminal!)))
    (is (false? (runtime/claim-terminal!)))
    (runtime/release-terminal!)
    (is (true? (runtime/claim-terminal!)))
    (finally (runtime/release-terminal!))))
