;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.render.ascii
  (:require
   [clojure.string :as str]
   [leaf-fall.model.config :as config]
   [leaf-fall.model.core :as model]
   [leaf-fall.render.cells :as cells]))

(defn- painted-grid [world]
  (let [simulation-config (:config world)
        rows (config/grid-height simulation-config)
        columns (config/grid-width simulation-config)]
    (reduce (fn [grid {:keys [row column glyph]}]
              (if (and (<= 0 row) (< row rows)
                       (<= 0 column) (< column columns))
                (assoc-in grid [row column] glyph)
                grid))
            (vec (repeat rows (vec (repeat columns \space))))
            (cells/visible-cells world))))

(defn render-string
  "Render the current phase as text. :columns/:rows request a centered crop."
  ([world]
   (render-string world {}))
  ([world {:keys [columns rows]}]
   (let [simulation-config (:config world)
         width (config/grid-width simulation-config)
         height (config/grid-height simulation-config)
         columns (min width (or columns width))
         rows (min height (or rows height))]
     (when-not (and (pos-int? columns) (pos-int? rows))
       (throw (ex-info "ASCII dimensions must be positive integers"
                       {:columns columns :rows rows})))
     (let [grid (painted-grid world)
           start-column (quot (- width columns) 2)
           start-row (max 0 (- height rows))
           header (format (str "LEAF FALL  phase:%s  cycle:%d  leaves:%d/%d  "
                               "seed:%d  tick:%d%s")
                          (str/upper-case (model/phase-name world))
                          (:cycle world)
                          (model/active-leaf-count world)
                          (count (:leaf-templates world))
                          (:seed world)
                          (get-in world [:clock :tick])
                          (if (get-in world [:clock :paused?]) "  PAUSED" ""))
           lines (for [row (range start-row (+ start-row rows))]
                   (apply str (subvec (nth grid row)
                                      start-column
                                      (+ start-column columns))))]
       (str header "\n" (str/join "\n" lines))))))

(defn render [world]
  (println (render-string world)))
