(ns test-runner
  (:require
   [clojure.test :as test]
   [sim.clock-test]
   [sim.config-test]
   [sim.controller-test]
   [sim.model-test]
   [sim.noise-test]
   [sim.random-test]
   [sim.runtime-test]
   [sim.theme-test]))

(def test-namespaces
  '[sim.clock-test
    sim.config-test
    sim.controller-test
    sim.model-test
    sim.noise-test
    sim.random-test
    sim.runtime-test
    sim.theme-test])

(defn -main [& _args]
  (let [{:keys [fail error]} (apply test/run-tests test-namespaces)]
    (when (pos? (+ fail error))
      (System/exit 1))))
