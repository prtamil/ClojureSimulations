;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns test-runner
  (:require
   [clojure.test :as test]
   [leaf-fall.app.controller-test]
   [leaf-fall.app.runtime-test]
   [leaf-fall.model.config-test]
   [leaf-fall.model.core-test]
   [leaf-fall.model.growth-test]
   [leaf-fall.model.leaf-test]
   [leaf-fall.model.raster-test]
   [leaf-fall.render.ascii-test]
   [leaf-fall.render.cells-test]
   [leaf-fall.render.lanterna-test]
   [leaf-fall.render.batch-test]
   [leaf-fall.render.quil-test]
   [leaf-fall.utility.clock-test]
   [leaf-fall.utility.random-test]
   [leaf-fall.terminal.framework-test]))

(def test-namespaces
  '[leaf-fall.app.controller-test
    leaf-fall.app.runtime-test
    leaf-fall.model.config-test
    leaf-fall.model.core-test
    leaf-fall.model.growth-test
    leaf-fall.model.leaf-test
    leaf-fall.model.raster-test
    leaf-fall.render.ascii-test
    leaf-fall.render.cells-test
    leaf-fall.render.lanterna-test
    leaf-fall.render.batch-test
    leaf-fall.render.quil-test
    leaf-fall.utility.clock-test
    leaf-fall.utility.random-test
    leaf-fall.terminal.framework-test])

(defn -main [& _]
  (let [{:keys [fail error]} (apply test/run-tests test-namespaces)]
    (shutdown-agents)
    (when (pos? (+ fail error))
      (System/exit 1))))
