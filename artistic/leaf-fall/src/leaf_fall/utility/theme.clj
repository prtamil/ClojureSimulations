;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.utility.theme)

;; RGB translations of the xterm-256 colors used by leaf_fall.c.
(def role-colors
  {:trunk [175 95 0]       ; xterm 130
   :branch [135 95 0]      ; xterm 94
   :leaf [95 255 0]        ; xterm 82
   :fall-head [255 255 255] ; xterm 231
   :fall-trail [0 255 0]}) ; xterm 46

(defn cell-rgb [{:keys [role]}]
  (get role-colors role [255 255 255]))
