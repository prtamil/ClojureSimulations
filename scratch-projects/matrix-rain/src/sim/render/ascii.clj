;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
;;
;; ASCII LEVEL GUIDE
;; -----------------
;; Rendering is a pure projection of glyphs already stored in the world.
(ns sim.render.ascii
  (:require [clojure.string :as str]
            [sim.model.config :as config]
            [sim.utility.theme :as theme]))

(defn visible-cells
  "Return visible stream cells as {:x :y :depth :glyph} maps."
  [world]
  (let [width (config/grid-width (:config world))
        height (config/grid-height (:config world))]
    (for [{:keys [column head-y glyphs]} (:drops world)
          [depth glyph] (map-indexed vector glyphs)
          :let [row (int (Math/floor (- head-y depth)))]
          :when (and (<= 0 column (dec width))
                     (<= 0 row (dec height)))]
      {:x column
       :y row
       :depth depth
       :length (count glyphs)
       :glyph glyph})))

(defn- preview-cells [world columns rows]
  (let [width (config/grid-width (:config world))
        height (config/grid-height (:config world))]
    (reduce
     (fn [cells {:keys [x y depth] :as cell}]
       (let [screen-cell [(min (dec columns) (int (* x (/ columns width))))
                          (min (dec rows) (int (* y (/ rows height))))]
             current (get cells screen-cell)]
         (if (or (nil? current) (< depth (:depth current)))
           (assoc cells screen-cell cell)
           cells)))
     {}
     (visible-cells world))))

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
                          (:tick world)
                          (:elapsed-seconds world)
                          (count (:drops world))
                          (:speed-mult world)
                          (:name (theme/current-theme world))
                          (if (:paused? world) "  PAUSED" ""))
           lines (for [y (range rows)]
                   (apply str
                          (for [x (range columns)]
                            (get-in cells [[x y] :glyph] \space))))]
       (str header "\n" (str/join "\n" lines))))))

(defn render [world]
  (println (render-string world)))

(comment
  ;; ASCII GUIDE FOR A REPL PROMPT
  (require '[clojure.string :as str]
           '[sim.render.ascii :as ascii]
           '[sim.model.core :as model]
           '[sim.utility.theme :as theme]
           :reload)

  (def before (model/initial-world 42))
  (ascii/render before)

  (def after
    (model/update-world-steps before 60 (/ 1.0 60.0)))
  (ascii/render after)

  ;; Re-rendering the same world is byte-for-byte identical.
  (= (ascii/render-string after) (ascii/render-string after))
  (def text (ascii/render-string after))
  (assert (str/includes? text "tick:60"))
  (assert (str/includes? text "streams:15"))

  (println (ascii/render-string after {:columns 20 :rows 10}))
  (take 10 (ascii/visible-cells after))

  (def themed-world (theme/cycle-theme after 1))
  (assert (str/includes? (ascii/render-string themed-world) "theme:CYAN"))

  ;; Keep randomness and stream evolution in sim.model.core. ASCII only chooses
  ;; which already-generated glyph is visible in each preview cell.

  (require '[clojure.test :as test]
           '[sim.model.core-test]
           :reload)
  (test/run-tests 'sim.model.core-test)
  )
