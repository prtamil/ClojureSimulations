;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns gui
  (:require
   [driver :as driver]
   [bonsai.app.runtime :as runtime]))

(defn -main [& _]
  (driver/start-gui!)
  ;; Processing starts on its own animation thread. Keep a command-line JVM
  ;; alive until the window's close callback releases the renderer claim.
  (while @runtime/gui-running?
    (Thread/sleep 50))
  (shutdown-agents)
  (System/exit 0))
