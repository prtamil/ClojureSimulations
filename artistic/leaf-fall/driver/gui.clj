;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns gui
  (:require
   [driver :as driver]
   [leaf-fall.app.runtime :as runtime]))

(defn -main [& _]
  (driver/start-gui!)
  ;; `defsketch` starts Processing on its own animation thread and returns.
  ;; Keep the command-line entry point alive only while that window is open,
  ;; then terminate the standalone JVM. REPL callers use driver/start-gui!
  ;; directly and are therefore unaffected by this process-level exit.
  (while @runtime/gui-running?
    (Thread/sleep 50))
  (shutdown-agents)
  (System/exit 0))
