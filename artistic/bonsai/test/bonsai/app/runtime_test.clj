(ns bonsai.app.runtime-test
  (:require
   [bonsai.app.runtime :as runtime]
   [clojure.test :refer [deftest is]]))

(deftest renderer-lifecycles-can-be-reclaimed
  (try
    (runtime/release-gui!)
    (runtime/release-terminal!)
    (is (true? (runtime/claim-gui!)))
    (is (false? (runtime/claim-gui!)))
    (is (true? (runtime/claim-terminal!)))
    (is (false? (runtime/claim-terminal!)))
    (finally
      (runtime/release-gui!)
      (runtime/release-terminal!))))
