(ns test-runner
  (:require
   [clojure.test :as test]
   [sim.utility.clock-test]
   [sim.model.config-test]
   [sim.app.controller-test]
   [sim.render.ascii-test]
   [sim.render.lanterna-test]
   [sim.render.projection-test]
   [sim.model.core-test]
   [sim.model.field-test]
   [sim.utility.noise-test]
   [sim.utility.random-test]
   [sim.app.runtime-test]
   [sim.terminal.framework-test]))

(def test-namespaces
  '[sim.utility.clock-test
    sim.model.config-test
    sim.app.controller-test
    sim.render.ascii-test
    sim.render.lanterna-test
    sim.render.projection-test
    sim.model.core-test
    sim.model.field-test
    sim.utility.noise-test
    sim.utility.random-test
    sim.app.runtime-test
    sim.terminal.framework-test])

(defn -main [& _args]
  (let [{:keys [fail error]} (apply test/run-tests test-namespaces)]
    (when (pos? (+ fail error))
      (System/exit 1))))

