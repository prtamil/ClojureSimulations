;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.render.ascii
  "Place projected glyphs into deterministic ASCII preview cells."
  (:require
   [clojure.string :as str]
   [sim.model.config :as config]
   [sim.model.projection :as projection]
   [sim.utility.theme :as theme]))

(defn- preview-cells [world columns rows]
  (let [width (config/grid-width (:config world))
        height (config/grid-height (:config world))]
    (reduce
     (fn [cells {:keys [position depth] :as glyph}]
       (let [[x y] position
             screen-cell [(min (dec columns) (int (* x (/ columns width))))
                          (min (dec rows) (int (* y (/ rows height))))]
             current (get cells screen-cell)]
         (if (or (nil? current) (< depth (:depth current)))
           (assoc cells screen-cell glyph)
           cells)))
     {}
     (projection/visible-glyphs world))))

(defn render-string
  "Return a deterministic headless Matrix Rain view."
  ([world] (render-string world {}))
  ([world {:keys [columns rows]}]
   (let [width (config/grid-width (:config world))
         height (config/grid-height (:config world))
         columns (or columns width)
         rows (or rows height)]
     (when-not (and (pos-int? columns) (pos-int? rows))
       (throw (ex-info "ASCII dimensions must be positive integers"
                       {:columns columns :rows rows})))
     (let [columns (min columns width)
           rows (min rows height)
           cells (preview-cells world columns rows)
           header (format (str "tick:%d  time:%.2fs  streams:%d  "
                               "speed:x%.2f  theme:%s%s")
                          (get-in world [:clock :tick])
                          (get-in world [:clock :elapsed-seconds])
                          (count (:drops world))
                          (:speed-multiplier world)
                          (:name (theme/current-theme world))
                          (if (get-in world [:clock :paused?]) "  PAUSED" ""))
           lines (for [y (range rows)]
                   (apply str
                          (for [x (range columns)]
                            (get-in cells [[x y] :character] \space))))]
       (str header "\n" (str/join "\n" lines))))))

(defn render [world]
  (println (render-string world)))

(comment
  (require '[sim.render.ascii :as ascii]
           '[sim.model.core :as model]
           '[sim.model.projection :as projection]
           :reload)

  (def before (model/initial-world 42))
  (def after
    (model/advance-world-steps before 60 (/ 1.0 60.0)))

  (ascii/render after)
  (println (ascii/render-string after {:columns 20 :rows 10}))
  (take 10 (projection/visible-glyphs after))
  )
