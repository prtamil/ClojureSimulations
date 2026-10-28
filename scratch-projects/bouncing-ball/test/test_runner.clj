(ns test-runner
  (:require
   [clojure.test :as test]
   [sim.utility.clock-test]
   [sim.model.config-test]
   [sim.app.controller-test]
   [sim.model.ball-test]
   [sim.model.core-test]
   [sim.terminal.framework-test]
   [sim.render.lanterna-test]
   [sim.app.runtime-test]
   [sim.utility.theme-test]))

(def test-namespaces
  '[sim.utility.clock-test
    sim.model.config-test
    sim.app.controller-test
    sim.model.ball-test
    sim.model.core-test
    sim.terminal.framework-test
    sim.render.lanterna-test
    sim.app.runtime-test
    sim.utility.theme-test])

(defn -main [& _args]
  (let [{:keys [fail error]} (apply test/run-tests test-namespaces)]
    (when (pos? (+ fail error))
      (System/exit 1))))
