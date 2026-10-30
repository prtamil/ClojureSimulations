;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.render.projection
  "Select the model values visible to every renderer.

  WHY: ASCII, Lanterna, and Quil should agree about what is visible without
  knowing particle lifecycle details or duplicating view-selection rules.

  DATA: project-world returns the scene shape documented below.
  OPERATIONS: select visible samples and public particle attributes.
  BOUNDARY: this namespace does not calculate motion or draw glyphs/pixels."
  (:require
   [sim.model.config :as config]))

;; {:pattern :particles
;;  :samples {[x y] {:position [x y]
;;                   :intensity 0.8
;;                   :band 2
;;                   :velocity [vx vy]}} ; velocity only for vector samples
;;  :particles [{:position [x y] :band 2}]
;;  :flash-intensity 0.5}

(defn- visible-samples [world]
  (if (config/particle-pattern? (get-in world [:view :pattern]))
    (:trail world)
    (get-in world [:view :samples])))

(defn- visible-particles [world]
  (if (config/particle-pattern? (get-in world [:view :pattern]))
    (mapv #(select-keys % [:position :band]) (:particles world))
    []))

(defn project-world [world]
  {:pattern (get-in world [:view :pattern])
   :samples (visible-samples world)
   :particles (visible-particles world)
   :flash-intensity (:flash-intensity world)})
