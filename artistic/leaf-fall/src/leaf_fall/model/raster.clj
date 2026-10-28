;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.model.raster
  "Rasterize tree geometry into sparse bark cells and leaf templates."
  (:require
   [leaf-fall.model.config :as config]
   [leaf-fall.utility.random :as random]))

(def ^:private leaf-glyphs [\* \@ \& \# \% \o \~])

(defn in-grid?
  [simulation-config row column]
  (and (<= 0 row) (< row (config/grid-height simulation-config))
       (<= 0 column) (< column (config/grid-width simulation-config))))

(defn cell-at
  "Return the sparse cell at row and column, or nil outside the grid."
  [simulation-config cells row column]
  (when (in-grid? simulation-config row column)
    (get cells [row column])))

(defn- priority [role]
  (case role
    :trunk 2
    :branch 1
    0))

(defn- paint-cell [simulation-config cells row column glyph role]
  (if-not (in-grid? simulation-config row column)
    cells
    (let [existing (get cells [row column])]
      (if (> (priority (:role existing)) (priority role))
        cells
        (assoc cells [row column]
               {:row row :column column :glyph glyph :role role})))))

(defn- branch-glyph [role row-step column-step]
  (cond
    (= role :trunk) \|
    (zero? column-step) \|
    (zero? row-step) \-
    (= (neg? row-step) (pos? column-step)) \/
    :else \\))

(defn- line-points
  "Inclusive integer points for a line, using Bresenham's algorithm."
  [[start-row start-column] [end-row end-column]]
  (let [column-distance (Math/abs (long (- end-column start-column)))
        row-distance (Math/abs (long (- end-row start-row)))
        column-step (cond (< start-column end-column) 1
                          (> start-column end-column) -1
                          :else 0)
        row-step (cond (< start-row end-row) 1
                       (> start-row end-row) -1
                       :else 0)]
    (loop [row start-row
           column start-column
           error (- column-distance row-distance)
           points []]
      (let [points (conj points [row column])]
        (if (and (= row end-row) (= column end-column))
          points
          (let [twice-error (* 2 error)
                move-column? (> twice-error (- row-distance))
                move-row? (< twice-error column-distance)]
            (recur (if move-row? (+ row row-step) row)
                   (if move-column? (+ column column-step) column)
                   (+ error
                      (if move-column? (- row-distance) 0)
                      (if move-row? column-distance 0))
                   points)))))))

(defn- paint-segment [simulation-config cells {:keys [from to role]}]
  (let [[start-row start-column] from
        [end-row end-column] to
        row-step (compare end-row start-row)
        column-step (compare end-column start-column)
        glyph (branch-glyph role row-step column-step)]
    (reduce (fn [painted [row column]]
              (paint-cell simulation-config painted row column glyph role))
            cells
            (line-points from to))))

(defn- paint-mark [simulation-config cells {:keys [position glyph role]}]
  (let [[row column] position]
    (paint-cell simulation-config cells row column glyph role)))

(defn- foliage-sites [simulation-config {:keys [center radius]}]
  (let [[center-row center-column] center
        aspect (get-in simulation-config [:tree :foliage-aspect])
        column-radius (int (Math/ceil (/ radius (Math/sqrt aspect))))]
    (for [row (range (- center-row radius) (inc (+ center-row radius)))
          column (range (- center-column column-radius)
                        (inc (+ center-column column-radius)))
          :let [row-offset (- row center-row)
                column-offset (- column center-column)
                distance (+ (* row-offset row-offset)
                            (* aspect column-offset column-offset))]
          :when (and (in-grid? simulation-config row column)
                     (<= distance (* radius radius)))]
      [row column distance])))

(defn- maybe-add-leaf
  [simulation-config [rng leaves occupied] [row column distance] radius]
  (let [base (get-in simulation-config [:tree :foliage-density-base])
        falloff (get-in simulation-config [:tree :foliage-density-falloff])
        threshold (+ base (* falloff (/ distance (inc (* radius radius)))))
        [rng roll] (random/unit rng)]
    (if (or (contains? occupied [row column]) (<= roll threshold))
      [rng leaves occupied]
      (let [[rng glyph-index] (random/index rng (count leaf-glyphs))
            [rng delay] (random/between
                         rng 0.0
                         (get-in simulation-config
                                 [:fall :start-delay-max-seconds]))
            speeds (get-in simulation-config [:fall :speed-options])
            [rng speed-index] (random/index rng (count speeds))]
        [rng
         (conj leaves {:origin [row column]
                       :glyph (nth leaf-glyphs glyph-index)
                       :delay-seconds delay
                       :speed (nth speeds speed-index)})
         (conj occupied [row column])]))))

(defn- scatter-foliage [simulation-config cells foliage seed]
  (let [initial-state [(random/derive-state seed 0x00dec0de)
                       []
                       (set (keys cells))]]
    (second
     (reduce
      (fn [state {:keys [radius] :as cloud}]
        (reduce #(maybe-add-leaf simulation-config %1 %2 radius)
                state
                (foliage-sites simulation-config cloud)))
      initial-state
      foliage))))

(defn bake-tree
  "Create sparse bark cells and immutable leaf templates from tree geometry."
  [simulation-config tree seed]
  (let [simulation-config (config/validate! simulation-config)
        cells (reduce (partial paint-segment simulation-config)
                      {}
                      (:segments tree))
        cells (reduce (partial paint-mark simulation-config)
                      cells
                      (:marks tree))
        leaves (scatter-foliage simulation-config cells (:foliage tree) seed)]
    {:cells cells :leaves leaves}))
