;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.render.batch)

(def role-order
  "Painter order for the five colors in the ASCII scene."
  [:trunk :branch :leaf :fall-trail :fall-head])

(defn- blank-row [column-count]
  (let [row (char-array column-count)]
    (java.util.Arrays/fill row \space)
    row))

(defn- blank-role-grid [row-count column-count]
  (vec (repeatedly row-count #(blank-row column-count))))

(defn cell-rows
  "Batch semantic cells into monospaced row strings grouped by color role.

  Thousands of individually positioned glyphs become at most
  (role-count * row-count) text operations. Later cells of the same role win
  at an occupied coordinate, matching the model's painter ordering."
  [row-count column-count cells]
  (let [role-grids
        (into {}
              (map (fn [role]
                     [role (blank-role-grid row-count column-count)]))
              role-order)]
    (doseq [{:keys [role row column glyph]} cells
            :when (and (contains? role-grids role)
                       (char? glyph)
                       (<= 0 row) (< row row-count)
                       (<= 0 column) (< column column-count))]
      (aset-char ^chars (nth (get role-grids role) row) column glyph))
    (into []
          (comp
           (mapcat
            (fn [role]
              (map-indexed
               (fn [row-index row]
                 {:role role
                  :row row-index
                  :text (String. ^chars row)})
               (get role-grids role))))
           (remove #(.isBlank ^String (:text %))))
          role-order)))
