;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
;;
;; TREE-TO-CHARACTER RASTERIZER
;; ----------------------------
;; Growth produces geometry: line segments and round foliage clouds. This
;; namespace turns that geometry into a flat vector of character cells.
;;
;; Read `bake-tree` at the bottom first. Its pseudocode is:
;;
;;   begin with an empty sparse cell map
;;   paint every branch with Bresenham's line algorithm
;;   paint every foliage cloud
;;   paint the pot last so it stays in front
;;   return the completed canvas
(ns bonsai.model.raster
  (:require
   [bonsai.model.config :as config]
   [bonsai.utility.random :as random]))

(def ^:private thick-bark-glyphs (vec "MHHM[]()"))
(def ^:private medium-bark-glyphs (vec "|[]/\\"))
(def ^:private thin-bark-glyphs (vec "|/\\"))

(defn cell-at
  "Return the semantic cell at row/column, or nil outside the grid."
  [simulation-config cells row column]
  (when (and (<= 0 row) (< row (config/grid-height simulation-config))
             (<= 0 column) (< column (config/grid-width simulation-config)))
    (get cells [row column])))

(defn- paint-cell
  "Place one semantic cell when it is in bounds.

  Foliage cannot cover existing bark; this keeps the tree structure legible."
  [cells simulation-config row column cell]
  (if (or (neg? row) (>= row (config/grid-height simulation-config))
          (neg? column) (>= column (config/grid-width simulation-config)))
    cells
    (let [coordinate [row column]
          existing (get cells coordinate)
          bark-role? #{:trunk-dark :trunk-mid :trunk-light}]
      (if (and (bark-role? (:role existing)) (= :foliage (:role cell)))
        cells
        (assoc cells coordinate (assoc cell :row row :column column))))))

(defn- bark-cell-for-depth [depth sample]
  (let [[glyphs role color-index]
        (cond
          (<= depth 1) [thick-bark-glyphs :trunk-dark 0]
          (<= depth 3) [medium-bark-glyphs :trunk-mid 1]
          :else [thin-bark-glyphs :trunk-light 2])]
    {:glyph (nth glyphs (mod sample (count glyphs)))
     :role role
     :color-index color-index}))

(defn- line-glyph
  "Choose a character that visually leans in the line's direction."
  [depth horizontal-distance vertical-distance step-x step-y]
  (cond
    (> horizontal-distance (* vertical-distance 2))
    (if (<= depth 1) \= \-)

    (> vertical-distance (* horizontal-distance 2))
    (if (<= depth 1) \H \|)

    (= step-x step-y)
    (if (<= depth 1) \M \\)

    :else
    (if (<= depth 1) \M \/)))

(defn- paint-branch-segment
  "Paint one branch with Bresenham's integer line algorithm."
  [canvas simulation-config {:keys [x0 y0 x1 y1 depth]}]
  (let [start-x (int (+ x0 0.5))
        start-y (int (+ y0 0.5))
        end-x (int (+ x1 0.5))
        end-y (int (+ y1 0.5))
        horizontal-distance (Math/abs (long (- end-x start-x)))
        vertical-distance (Math/abs (long (- end-y start-y)))
        step-x (if (< start-x end-x) 1 -1)
        step-y (if (< start-y end-y) 1 -1)
        initial-error (- horizontal-distance vertical-distance)
        bark-cell (bark-cell-for-depth
                   depth
                   (Math/abs (long (+ start-x start-y))))
        glyph (line-glyph depth horizontal-distance vertical-distance
                          step-x step-y)]
    (loop [canvas canvas
           x start-x
           y start-y
           error initial-error]
      (let [canvas (paint-cell canvas simulation-config y x
                               (assoc bark-cell :glyph glyph))]
        (if (and (= x end-x) (= y end-y))
          canvas
          (let [twice-error (* 2 error)
                move-x? (> twice-error (- vertical-distance))
                error (if move-x? (- error vertical-distance) error)
                x (if move-x? (+ x step-x) x)
                move-y? (< twice-error horizontal-distance)
                error (if move-y? (+ error horizontal-distance) error)
                y (if move-y? (+ y step-y) y)]
            (recur canvas x y error)))))))

(defn- paint-foliage-cloud
  "Paint one soft-edged ellipse of seeded leaf characters."
  [canvas rng simulation-config active-theme {:keys [cx cy radius]}]
  (let [center-x (int (+ cx 0.5))
        center-y (int (+ cy 0.5))
        cell-aspect (get-in simulation-config [:tree :cell-aspect])
        vertical-radius (/ radius cell-aspect)
        first-row (- center-y (int (inc vertical-radius)))
        last-row (+ center-y (int (inc vertical-radius)))
        first-column (- center-x (int (inc radius)))
        last-column (+ center-x (int (inc radius)))
        leaf-glyphs (:leaves active-theme)]
    (loop [row first-row
           column first-column
           canvas canvas
           rng rng]
      (cond
        ;; The cloud is complete. Add its optional snow cap.
        (> row last-row)
        (let [snow-row (inc first-row)
              snow-tip (:snow-tip active-theme)
              canvas (if snow-tip
                       (paint-cell canvas simulation-config snow-row center-x
                                   {:glyph snow-tip
                                    :role :snow-tip
                                    :color-index 5})
                       canvas)]
          [canvas rng])

        ;; Continue on the next row.
        (> column last-column)
        (recur (inc row) first-column canvas rng)

        :else
        (let [offset-x (- column center-x)
              offset-y (* (- row center-y) cell-aspect)
              radius-squared (* radius radius)
              distance (+ (/ (* offset-x offset-x) radius-squared)
                          (/ (* offset-y offset-y) radius-squared))]
          (if (> distance 1.0)
            ;; Outside the ellipse.
            (recur row (inc column) canvas rng)
            (let [[rng skip-roll] (if (> distance 0.6)
                                    (random/unit rng)
                                    [rng 0.0])]
              (if (and (> distance 0.6) (> skip-roll 0.7))
                ;; Thin some leaves near the edge for a softer silhouette.
                (recur row (inc column) canvas rng)
                (let [[rng glyph-index] (random/index rng (count leaf-glyphs))
                      [rng color-index] (random/index rng 6)
                      canvas (paint-cell
                              canvas simulation-config row column
                              {:glyph (nth leaf-glyphs glyph-index)
                               :role :foliage
                               :color-index color-index})]
                  (recur row (inc column) canvas rng))))))))))

(defn- paint-pot
  "Paint the rim, textured walls, and solid bottom of the bonsai pot."
  [canvas simulation-config pot-top]
  (let [column-count (config/grid-width simulation-config)
        row-count (config/grid-height simulation-config)
        pot-width (-> (quot column-count 3)
                      (max 12)
                      (min (- column-count 4)))
        first-column (- (quot column-count 2) (quot pot-width 2))
        last-column (dec (+ first-column pot-width))
        rim-row (inc pot-top)
        pot-cell (fn [glyph] {:glyph glyph :role :pot :color-index 0})
        canvas
        (reduce (fn [canvas column]
                  (paint-cell
                   canvas simulation-config rim-row column
                   (pot-cell (cond
                               (= column (dec first-column)) \\
                               (= column (inc last-column)) \/
                               :else \_))))
                canvas
                (range (dec first-column) (+ last-column 2)))
        ;; Rows below the rim stay inside the logical grid by construction.
        rows-below-rim (-> (- row-count rim-row 1) (max 1) (min 4))]
    (loop [offset 0
           canvas canvas]
      (if (= offset rows-below-rim)
        canvas
        (let [row (+ rim-row 1 offset)]
          (cond
            (>= row row-count)
            canvas

            ;; Last row: close the pot with a solid textured bottom.
            (= offset (dec rows-below-rim))
            (let [canvas
                  (reduce (fn [canvas column]
                            (paint-cell
                             canvas simulation-config row column
                             (pot-cell (cond
                                         (= column first-column) \\
                                         (= column last-column) \/
                                         :else \:))))
                          canvas
                          (range first-column (inc last-column)))]
              (recur (inc offset) canvas))

            ;; Middle rows: side walls plus sparse soil dots.
            :else
            (let [canvas (-> canvas
                             (paint-cell simulation-config row first-column
                                         (pot-cell \|))
                             (paint-cell simulation-config row last-column
                                         (pot-cell \|)))
                  canvas
                  (reduce (fn [canvas column]
                            (if (zero? (bit-and (+ column row) 3))
                              (paint-cell canvas simulation-config row column
                                          (pot-cell \.))
                              canvas))
                          canvas
                          (range (inc first-column) last-column))]
              (recur (inc offset) canvas))))))))

(defn- paint-snow-tip
  "Place one snowflake just above a terminal branch point."
  [canvas simulation-config snow-glyph {:keys [x y]}]
  (let [column (int (+ x 0.5))
        row (dec (int (+ y 0.5)))]
    (paint-cell canvas simulation-config row column
                {:glyph snow-glyph :role :snow-tip :color-index 5})))

(defn bake-tree
  "Project tree geometry into a sparse map of semantic character cells."
  [simulation-config tree pot-top active-theme seed]
  (let [canvas-with-bark
        (reduce #(paint-branch-segment %1 simulation-config %2)
                {}
                (:segments tree))
        foliage-seed (random/u32 (bit-xor (long seed) 0xDEC0DE))
        [canvas-with-leaves _]
        (if (:bare? active-theme)
          [canvas-with-bark foliage-seed]
          (reduce (fn [[canvas rng] foliage]
                    (paint-foliage-cloud canvas rng simulation-config
                                          active-theme foliage))
                  [canvas-with-bark foliage-seed]
                  (:foliage tree)))
        canvas-with-snow
        (if-let [snow-glyph (:snow-tip active-theme)]
          (reduce #(paint-snow-tip %1 simulation-config snow-glyph %2)
                  canvas-with-leaves
                  (:tips tree))
          canvas-with-leaves)]
    (paint-pot canvas-with-snow simulation-config pot-top)))
