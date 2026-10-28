;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns test-runner
  (:require
   [clojure.test :as test]
   [bonsai.app.controller-test]
   [bonsai.app.runtime-test]
   [bonsai.model.config-test]
   [bonsai.model.core-test]
   [bonsai.model.growth-test]
   [bonsai.model.raster-test]
   [bonsai.render.ascii-test]
   [bonsai.render.cells-test]
   [bonsai.render.lanterna-test]
   [bonsai.render.visual-snapshot-test]
   [bonsai.utility.clock-test]
   [bonsai.utility.random-test]
   [bonsai.terminal.framework-test]))

(def test-namespaces
  '[bonsai.app.controller-test
    bonsai.app.runtime-test
    bonsai.model.config-test
    bonsai.model.core-test
    bonsai.model.growth-test
    bonsai.model.raster-test
    bonsai.render.ascii-test
    bonsai.render.cells-test
    bonsai.render.lanterna-test
    bonsai.render.visual-snapshot-test
    bonsai.utility.clock-test
    bonsai.utility.random-test
    bonsai.terminal.framework-test])

(defn -main [& _]
  (let [{:keys [fail error]} (apply test/run-tests test-namespaces)]
    (shutdown-agents)
    (when (pos? (+ fail error))
      (System/exit 1))))
