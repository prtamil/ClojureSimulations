;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.render.cells
  "Project model values into semantic cells for every renderer."
  (:require
   [leaf-fall.model.config :as config]
   [leaf-fall.model.leaf :as leaf]))

(defn- template-cell [{:keys [origin glyph]}]
  {:row (first origin)
   :column (second origin)
   :glyph glyph
   :role :leaf})

(defn- falling-cells [world particle]
  (if (leaf/waiting? particle)
    [(template-cell particle)]
    (let [head-row (leaf/head-row particle)
          origin-row (first (:origin particle))
          row-count (config/grid-height (:config world))
          trail-length (get-in world [:config :fall :trail-length])]
      (into []
            (comp
             (map (fn [offset]
                    {:row (- head-row offset)
                     :column (:column particle)
                     :glyph (:glyph particle)
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
                   (:leaves world))
    :falling (into (vec (vals (:cells world)))
                   (mapcat (partial falling-cells world))
                   (:falling-leaves world))
    []))
