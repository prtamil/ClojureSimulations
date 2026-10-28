;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
;; Headless renderer: world -> string. It never mutates or advances the model.
(ns bonsai.render.ascii
  (:require
   [clojure.string :as str]
   [bonsai.model.config :as config]
   [bonsai.model.core :as model]
   [bonsai.render.cells :as cells]
   [bonsai.utility.theme :as theme]))

(defn render-string
  "Return a text preview. Optional :columns/:rows center-crop the baked grid."
  ([world] (render-string world {}))
  ([world {:keys [columns rows]}]
   (let [simulation-config (:config world)
         grid-width (config/grid-width simulation-config)
         grid-height (config/grid-height simulation-config)
         columns (min grid-width (or columns grid-width))
         rows (min grid-height (or rows grid-height))
         start-column (quot (- grid-width columns) 2)
         start-row (max 0 (- grid-height rows))
         active-theme (theme/current-theme world)
         style (model/current-style world)
         header (format (str "BONSAI  style:%s (%s)  theme:%s  seed:%d  "
                             "wind:%d  tick:%d%s")
                        (:name style)
                        (:description style)
                        (:name active-theme)
                        (:seed world)
                        (:wind world)
                        (:tick world)
                        (if (:paused? world) "  PAUSED" ""))
         lines
         (for [row (range start-row (+ start-row rows))]
           (apply str
                  (for [column (range start-column (+ start-column columns))
                        :let [cell (model/cell-at world row column)]]
                    (or (cells/display-glyph world cell) \space))))]
     (str header "\n" (str/join "\n" lines)))))

(defn render [world]
  (println (render-string world)))

(comment
  (require '[bonsai.render.ascii :as ascii]
           '[bonsai.model.core :as model]
           :reload)
  (def spring (model/initial-world 42))
  (ascii/render spring)
  (ascii/render (model/cycle-style spring 1))
  (ascii/render (model/cycle-theme spring 3))
  )
