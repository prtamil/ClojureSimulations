;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.render.cells
  "Project model values into actual discrete grid cells for every renderer.

  WHY: ASCII, terminal, and Quil share leaf-template placement and falling
  trail visibility rules.
  DATA: returns semantic cells {:row :column :glyph :role}; retains no state.
  OPERATIONS: project the current phase into painter-ordered visible cells.
  BOUNDARY: motion, world phases, rasterized tree storage, and drawing stay out."
  (:require
   [leaf-fall.model.config :as config]
   [leaf-fall.model.leaf :as leaf]))

(defn- leaf-cell [position glyph]
  {:row (first position)
   :column (second position)
   :glyph glyph
   :role :leaf})

(defn- template-cell [{:keys [position glyph]}]
  (leaf-cell position glyph))

(defn- falling-cells [world falling-leaf]
  (if (leaf/waiting? falling-leaf)
    [(leaf-cell (:origin falling-leaf) (:glyph falling-leaf))]
    (let [head-row (leaf/head-row falling-leaf)
          origin-row (first (:origin falling-leaf))
          row-count (config/grid-height (:config world))
          trail-length (get-in world [:config :fall :trail-length])]
      (into []
            (comp
             (map (fn [offset]
                    {:row (- head-row offset)
                     :column (second (:position falling-leaf))
                     :glyph (:glyph falling-leaf)
                     :role (if (zero? offset) :fall-head :fall-trail)}))
             (filter (fn [{:keys [row]}]
                       (and (<= origin-row row) (< row row-count)))))
            (range trail-length -1 -1)))))

(defn visible-cells
  "Return this frame's semantic cells in painter order."
  [world]
  (case (:phase world)
    :reset []
    :display (into (vec (vals (:cells world)))
                   (map template-cell)
                    (:leaf-templates world))
    :falling (into (vec (vals (:cells world)))
                   (mapcat (partial falling-cells world))
                   (:falling-leaves world))
    []))
