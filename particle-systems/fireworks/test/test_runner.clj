;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns test-runner
  (:require
   [clojure.test :as test]
   [fireworks.app.controller-test]
   [fireworks.app.runtime-test]
   [fireworks.model.config-test]
   [fireworks.model.core-test]
   [fireworks.model.rocket-test]
   [fireworks.model.spark-test]
   [fireworks.render.ascii-test]
   [fireworks.render.cells-test]
   [fireworks.render.lanterna-test]
   [fireworks.terminal.framework-test]
   [fireworks.utility.clock-test]
   [fireworks.utility.random-test]
   [fireworks.utility.theme-test]))

(def test-namespaces
  '[fireworks.app.controller-test
    fireworks.app.runtime-test
    fireworks.model.config-test
    fireworks.model.core-test
    fireworks.model.rocket-test
    fireworks.model.spark-test
    fireworks.render.ascii-test
    fireworks.render.cells-test
    fireworks.render.lanterna-test
    fireworks.terminal.framework-test
    fireworks.utility.clock-test
    fireworks.utility.random-test
    fireworks.utility.theme-test])

(defn -main [& _]
  (let [{:keys [fail error]} (apply test/run-tests test-namespaces)]
    (shutdown-agents)
    (when (pos? (+ fail error))
      (System/exit 1))))
