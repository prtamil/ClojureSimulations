;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.model.raster
  "Rasterize tree geometry into bark cells and immutable leaf templates.

  WHY: Bresenham lines, foliage ellipses, drawing priority, and deterministic
  leaf scattering are shared discrete-grid rules, not growth rules.
  DATA: empty-scene and the cell/template shapes below document its results.
  OPERATIONS: place trunk/crown, scatter leaves, and query grid positions.
  BOUNDARY: continuous geometry, falling motion, phases, and devices stay out."
  (:require
   [leaf-fall.model.config :as config]
   [leaf-fall.utility.random :as random]))

;; Scene data

(def empty-scene
  {:cells {}
   :leaf-templates []})

;; A sparse cell:
;; {:row row :column column :glyph character :role :trunk}
;;
;; A leaf template:
;; {:position [row column] :glyph character
;;  :delay-seconds 1.5 :speed 9.0}

(def ^:private leaf-glyphs [\* \@ \& \# \% \o \~])

(defn in-grid?
  [simulation-config row column]
  (and (<= 0 row) (< row (config/grid-height simulation-config))
       (<= 0 column) (< column (config/grid-width simulation-config))))

(defn cell-at
  "Return the sparse cell at row and column, or nil outside the grid."
  [world row column]
  (when (in-grid? (:config world) row column)
    (get (:cells world) [row column])))

;; Placing bark

(defn- make-cell
  [row column glyph role]
  {:row row
   :column column
   :glyph glyph
   :role role})

(defn- drawing-priority
  [role]
  (case role
    :trunk 2
    :branch 1
    0))

(defn- put-cell
  [simulation-config cells row column glyph role]
  (if-not (in-grid? simulation-config row column)
    cells
    (let [position [row column]
          existing (get cells position)
          existing-wins? (> (drawing-priority (:role existing))
                            (drawing-priority role))]
      (if existing-wins?
        cells
        (assoc cells position (make-cell row column glyph role))))))

(defn- choose-branch-glyph
  [role row-step column-step]
  (cond
    (= role :trunk) \|
    (zero? column-step) \|
    (zero? row-step) \-
    (= (neg? row-step) (pos? column-step)) \/
    :else \\))

(defn- points-on-line
  "Return inclusive integer points for one Bresenham line."
  [[start-row start-column] [end-row end-column]]
  (let [column-distance (Math/abs (long (- end-column start-column)))
        row-distance (Math/abs (long (- end-row start-row)))
        column-step (cond
                      (< start-column end-column) 1
                      (> start-column end-column) -1
                      :else 0)
        row-step (cond
                   (< start-row end-row) 1
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

(defn- draw-segment
  [simulation-config cells role {:keys [start end]}]
  (let [[start-row start-column] start
        [end-row end-column] end
        row-step (compare end-row start-row)
        column-step (compare end-column start-column)
        glyph (choose-branch-glyph role row-step column-step)]
    (reduce
     (fn [cells [row column]]
       (put-cell simulation-config cells row column glyph role))
     cells
     (points-on-line start end))))

(defn- draw-mark
  [simulation-config cells {:keys [position glyph]}]
  (let [[row column] position]
    (put-cell simulation-config cells row column glyph :trunk)))

;; Scattering leaves

(defn- positions-in-foliage
  [simulation-config {:keys [center radius]}]
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
      {:position [row column]
       :distance distance})))

(defn- try-adding-leaf
  [leaf-state simulation-config {:keys [position distance]} radius]
  (let [base-density
        (get-in simulation-config [:tree :foliage-density-base])
        edge-falloff
        (get-in simulation-config [:tree :foliage-density-falloff])
        threshold (+ base-density
                     (* edge-falloff
                        (/ distance (inc (* radius radius)))))
        [random-state chance]
        (random/unit (:random-state leaf-state))
        leaf-state (assoc leaf-state :random-state random-state)]
    (if (or (contains? (:occupied leaf-state) position)
            (<= chance threshold))
      leaf-state
      (let [[random-state glyph-index]
            (random/index (:random-state leaf-state)
                          (count leaf-glyphs))
            [random-state delay]
            (random/between
             random-state
             0.0
             (get-in simulation-config
                     [:fall :start-delay-max-seconds]))
            speeds (get-in simulation-config [:fall :speed-options])
            [random-state speed-index]
            (random/index random-state (count speeds))
            template
            {:position position
             :glyph (nth leaf-glyphs glyph-index)
             :delay-seconds delay
             :speed (nth speeds speed-index)}]
        (-> leaf-state
            (assoc :random-state random-state)
            (update :leaf-templates conj template)
            (update :occupied conj position))))))

(defn- scatter-foliage
  [leaf-state simulation-config foliage]
  (reduce
   (fn [leaf-state {:keys [radius] :as cloud}]
     (reduce #(try-adding-leaf %1 simulation-config %2 radius)
             leaf-state
             (positions-in-foliage simulation-config cloud)))
   leaf-state
   foliage))

;; Public placement story

(defn place-trunk
  "Place trunk segments and root marks into a scene."
  [scene world]
  (let [simulation-config (:config world)
        trunk (get-in world [:tree :trunk])
        cells (reduce #(draw-segment simulation-config %1 :trunk %2)
                      (:cells scene)
                      (:segments trunk))
        cells (reduce #(draw-mark simulation-config %1 %2)
                      cells
                      (:marks trunk))]
    (assoc scene :cells cells)))

(defn place-crown
  "Place crown branches into a scene."
  [scene world]
  (let [simulation-config (:config world)
        branches (get-in world [:tree :crown :branches])
        cells
        (reduce
         (fn [cells {:keys [depth] :as branch}]
           (draw-segment simulation-config
                         cells
                         (if (<= depth 2) :trunk :branch)
                         branch))
         (:cells scene)
         branches)]
    (assoc scene :cells cells)))

(defn scatter-leaves
  "Scatter deterministic leaf templates through the crown foliage."
  [scene world]
  (let [first-state
        {:random-state (random/derive-state (:seed world) 0x00dec0de)
         :leaf-templates []
         :occupied (set (keys (:cells scene)))}
        complete-state
        (scatter-foliage first-state
                         (:config world)
                         (get-in world [:tree :crown :foliage]))]
    (assoc scene :leaf-templates (:leaf-templates complete-state))))
