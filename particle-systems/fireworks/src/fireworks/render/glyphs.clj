;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.render.glyphs
  "Project renderer-independent fireworks into semantic glyphs.

  WHY: ASCII, terminal, and Quil share clipping, overlap, exhaust, and glyph
  styling rules. DATA: returns a canvas keyed by actual grid positions, with
  the semantic glyph shape documented below. OPERATIONS: project a world.
  BOUNDARY: stage physics, clocks, palettes, and device drawing stay outside."
  (:require
   [fireworks.model.config :as config]))

;; A canvas maps [column row] to the visible glyph at that position:
;;
;; {[column row] {:column column
;;                :row row
;;                :glyph \*
;;                :role :spark
;;                :color-index 0
;;                :life 1.0
;;                :bold? true
;;                :dim? false}}

(defn- make-glyph [role glyph color-index life bold? dim?]
  {:glyph glyph
   :role role
   :color-index color-index
   :life life
   :bold? bold?
   :dim? dim?})

(defn- inside-grid? [simulation-config column row]
  (and (<= 0 column) (< column (config/grid-width simulation-config))
       (<= 0 row) (< row (config/grid-height simulation-config))))

(defn- put-glyph [canvas simulation-config column row glyph]
  (if (inside-grid? simulation-config column row)
    ;; A later firework overwrites an earlier one at the same position.
    (assoc canvas [column row] (assoc glyph :column column :row row))
    canvas))

(defn- place-rocket [canvas simulation-config firework]
  (let [[x y] (:position firework)
        column (int x)
        row (int y)
        color-index (:color-index firework)]
    (put-glyph canvas simulation-config column row
               (make-glyph :rocket \| color-index 1.0 true false))))

(defn- place-exhaust [canvas simulation-config firework]
  (let [[x y] (:position firework)]
    (put-glyph canvas simulation-config (int x) (inc (int y))
               (make-glyph :exhaust \' (:color-index firework)
                           0.5 false false))))

(defn- place-spark [canvas simulation-config spark]
  (let [[x y] (:position spark)
        life (:life spark)]
    (put-glyph canvas simulation-config (int x) (int y)
               (make-glyph :spark (:glyph spark) (:color-index spark)
                           life (> life 0.6) (< life 0.2)))))

(defn- place-sparks [canvas simulation-config firework]
  (reduce (fn [canvas spark]
            (place-spark canvas simulation-config spark))
          canvas
          (:sparks firework)))

(defn- place-firework [canvas simulation-config firework]
  (case (:stage firework)
    :flying (-> canvas
                (place-rocket simulation-config firework)
                (place-exhaust simulation-config firework))
    :bursting (place-sparks canvas simulation-config firework)
    canvas))

(defn- place-fireworks [canvas world]
  (reduce (fn [canvas firework]
            (place-firework canvas (:config world) firework))
          canvas
          (get-in world [:show :fireworks])))

(defn- order-glyphs [canvas]
  (->> canvas
       vals
       (sort-by (juxt :row :column))
       vec))

(defn project-show
  "Project every visible firework into glyphs in reading order."
  [world]
  (-> {}
      (place-fireworks world)
      order-glyphs))
