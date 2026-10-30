;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.model.raster
  "Rasterize calculated tree geometry onto a sparse character grid.

  WHY: Bresenham lines, foliage ellipses, overlap policy, seeded glyph choice,
  and pot layering are shared discrete-grid rules, not tree growth rules.
  DATA: a grid maps [row column] to the semantic cell shape documented below.
  OPERATIONS: place trunk, crown, pot, and query a cell.
  BOUNDARY: continuous geometry, world lifecycle, frame animation, and device
  drawing remain outside this namespace."
  (:require
   [bonsai.model.config :as config]
   [bonsai.utility.random :as random]
   [bonsai.utility.theme :as theme]))

;; Canvas data
;;
;; A canvas is a map from [row column] to a semantic cell:
;;
;; {[row column] {:row row
;;                :column column
;;                :glyph character
;;                :role :foliage
;;                :color-index 0}}

(def ^:private thick-bark-glyphs (vec "MHHM[]()"))
(def ^:private medium-bark-glyphs (vec "|[]/\\"))
(def ^:private thin-bark-glyphs (vec "|/\\"))
(def ^:private foliage-seed-mask 0xDEC0DE)

(defn- make-cell
  [glyph role color-index]
  {:glyph glyph
   :role role
   :color-index color-index})

(defn cell-at
  "Return the semantic cell at row/column, or nil outside the grid."
  [world row column]
  (let [simulation-config (:config world)]
    (when (and (<= 0 row) (< row (config/grid-height simulation-config))
               (<= 0 column) (< column (config/grid-width simulation-config)))
      (get (:cells world) [row column]))))

;; Placing individual cells

(defn- on-canvas?
  [simulation-config row column]
  (and (<= 0 row)
       (< row (config/grid-height simulation-config))
       (<= 0 column)
       (< column (config/grid-width simulation-config))))

(defn- bark-cell?
  [cell]
  (#{:trunk-dark :trunk-mid :trunk-light} (:role cell)))

(defn- put-cell
  "Put one cell on the canvas when the position is visible.

  Foliage cannot cover bark, keeping the structure of the tree legible."
  [canvas simulation-config row column cell]
  (if-not (on-canvas? simulation-config row column)
    canvas
    (let [position [row column]
          existing (get canvas position)
          foliage-over-bark? (and (bark-cell? existing)
                                  (= :foliage (:role cell)))]
      (if foliage-over-bark?
        canvas
        (assoc canvas position
               (assoc cell :row row :column column))))))

;; Drawing trunk and branch segments

(defn- choose-bark-cell
  [depth sample]
  (let [[glyphs role color-index]
        (cond
          (<= depth 1) [thick-bark-glyphs :trunk-dark 0]
          (<= depth 3) [medium-bark-glyphs :trunk-mid 1]
          :else [thin-bark-glyphs :trunk-light 2])]
    (make-cell (nth glyphs (mod sample (count glyphs)))
               role
               color-index)))

(defn- choose-line-glyph
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

(defn- draw-segment
  "Draw one segment with Bresenham's integer line algorithm."
  [canvas simulation-config {:keys [start end depth]}]
  (let [[start-x start-y] (mapv #(int (+ % 0.5)) start)
        [end-x end-y] (mapv #(int (+ % 0.5)) end)
        horizontal-distance (Math/abs (long (- end-x start-x)))
        vertical-distance (Math/abs (long (- end-y start-y)))
        step-x (if (< start-x end-x) 1 -1)
        step-y (if (< start-y end-y) 1 -1)
        first-error (- horizontal-distance vertical-distance)
        bark (choose-bark-cell
              depth
              (Math/abs (long (+ start-x start-y))))
        glyph (choose-line-glyph depth
                                 horizontal-distance
                                 vertical-distance
                                 step-x
                                 step-y)]
    (loop [canvas canvas
           x start-x
           y start-y
           error first-error]
      (let [canvas (put-cell canvas simulation-config y x
                             (assoc bark :glyph glyph))]
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

;; Drawing foliage and snow

(defn- foliage-bounds
  [simulation-config {:keys [center radius]}]
  (let [[center-x center-y] (mapv #(int (+ % 0.5)) center)
        cell-aspect (get-in simulation-config [:tree :cell-aspect])
        vertical-radius (/ radius cell-aspect)]
    {:first-row (- center-y (int (inc vertical-radius)))
     :last-row (+ center-y (int (inc vertical-radius)))
     :first-column (- center-x (int (inc radius)))
     :last-column (+ center-x (int (inc radius)))}))

(defn- distance-from-foliage-center
  [simulation-config {:keys [center radius]} row column]
  (let [[center-x center-y] (mapv #(int (+ % 0.5)) center)
        cell-aspect (get-in simulation-config [:tree :cell-aspect])
        x-distance (- column center-x)
        y-distance (* (- row center-y) cell-aspect)
        radius-squared (* radius radius)]
    (+ (/ (* x-distance x-distance) radius-squared)
       (/ (* y-distance y-distance) radius-squared))))

(defn- draw-foliage
  "Draw one soft-edged ellipse of seeded leaf characters."
  [canvas random-state simulation-config active-theme foliage]
  (let [{:keys [first-row last-row first-column last-column]}
        (foliage-bounds simulation-config foliage)
        leaf-glyphs (:leaves active-theme)]
    (loop [row first-row
           column first-column
           canvas canvas
           random-state random-state]
      (cond
        (> row last-row)
        [canvas random-state]

        (> column last-column)
        (recur (inc row) first-column canvas random-state)

        :else
        (let [distance (distance-from-foliage-center
                        simulation-config foliage row column)
              outside? (> distance 1.0)
              near-edge? (> distance 0.6)]
          (if outside?
            (recur row (inc column) canvas random-state)
            (let [[random-state skip-chance]
                  (if near-edge?
                    (random/unit random-state)
                    [random-state 0.0])
                  skip? (and near-edge? (> skip-chance 0.7))]
              (if skip?
                (recur row (inc column) canvas random-state)
                (let [[random-state glyph-index]
                      (random/index random-state (count leaf-glyphs))
                      [random-state color-index]
                      (random/index random-state 6)
                      leaf (make-cell (nth leaf-glyphs glyph-index)
                                      :foliage
                                      color-index)]
                  (recur row
                         (inc column)
                         (put-cell canvas simulation-config row column leaf)
                         random-state))))))))))

(defn- draw-snow
  [canvas simulation-config snow-glyph {:keys [position]}]
  (let [[x y] position
        column (int (+ x 0.5))
        row (dec (int (+ y 0.5)))]
    (put-cell canvas simulation-config row column
              (make-cell snow-glyph :snow-tip 5))))

;; Drawing the pot

(defn- pot-cell
  [glyph]
  (make-cell glyph :pot 0))

(defn- draw-pot-rim
  [canvas simulation-config row first-column last-column]
  (reduce
   (fn [canvas column]
     (put-cell canvas simulation-config row column
               (pot-cell (cond
                           (= column (dec first-column)) \\
                           (= column (inc last-column)) \/
                           :else \_))))
   canvas
   (range (dec first-column) (+ last-column 2))))

(defn- draw-pot-bottom
  [canvas simulation-config row first-column last-column]
  (reduce
   (fn [canvas column]
     (put-cell canvas simulation-config row column
               (pot-cell (cond
                           (= column first-column) \\
                           (= column last-column) \/
                           :else \:))))
   canvas
   (range first-column (inc last-column))))

(defn- draw-pot-walls
  [canvas simulation-config row first-column last-column]
  (let [canvas (-> canvas
                   (put-cell simulation-config row first-column
                             (pot-cell \|))
                   (put-cell simulation-config row last-column
                             (pot-cell \|)))]
    (reduce
     (fn [canvas column]
       (if (zero? (bit-and (+ column row) 3))
         (put-cell canvas simulation-config row column (pot-cell \.))
         canvas))
     canvas
     (range (inc first-column) last-column))))

(defn- draw-pot
  [canvas simulation-config top-row]
  (let [columns (config/grid-width simulation-config)
        rows (config/grid-height simulation-config)
        width (-> (quot columns 3)
                  (max 12)
                  (min (- columns 4)))
        first-column (- (quot columns 2) (quot width 2))
        last-column (dec (+ first-column width))
        rim-row (inc top-row)
        wall-height (-> (- rows rim-row 1) (max 1) (min 4))
        canvas (draw-pot-rim canvas simulation-config rim-row
                             first-column last-column)]
    (reduce
     (fn [canvas offset]
       (let [row (+ rim-row 1 offset)
             bottom? (= offset (dec wall-height))]
         (cond
           (>= row rows) canvas
           bottom? (draw-pot-bottom canvas simulation-config row
                                    first-column last-column)
           :else (draw-pot-walls canvas simulation-config row
                                 first-column last-column))))
     canvas
     (range wall-height))))

;; Public placement story

(defn place-trunk
  "Place the main trunk on the canvas."
  [canvas world]
  (reduce #(draw-segment %1 (:config world) %2)
          canvas
          (get-in world [:tree :trunk :segments])))

(defn place-crown
  "Place crown branches, foliage, and optional snow on the canvas."
  [canvas world]
  (let [simulation-config (:config world)
        crown (get-in world [:tree :crown])
        active-theme (theme/current-theme world)
        canvas-with-branches
        (reduce #(draw-segment %1 simulation-config %2)
                canvas
                (:branches crown))
        foliage-seed
        (random/u32 (bit-xor (long (:seed world))
                             foliage-seed-mask))
        [canvas-with-foliage _]
        (if (:bare? active-theme)
          [canvas-with-branches foliage-seed]
          (reduce
           (fn [[canvas random-state] foliage]
             (draw-foliage canvas random-state simulation-config
                           active-theme foliage))
           [canvas-with-branches foliage-seed]
           (:foliage crown)))]
    (if-let [snow-glyph (:snow-tip active-theme)]
      (reduce #(draw-snow %1 simulation-config snow-glyph %2)
              canvas-with-foliage
              (:tips crown))
      canvas-with-foliage)))

(defn place-pot
  "Place the pot last so that it appears in front of the tree."
  [canvas world]
  (draw-pot canvas
            (:config world)
            (get-in world [:pot :top-row])))
