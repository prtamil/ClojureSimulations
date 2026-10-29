;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.model.growth
  "Calculate the pot, trunk, and crown of a bonsai tree."
  (:require
   [bonsai.model.config :as config]
   [bonsai.utility.random :as random]))

;; Tree data
;;
;; These values document the complete geometry shape. Calculation functions
;; fill them with ordinary maps and vectors; there are no hidden objects.

(def empty-pot
  {:top-row nil})

(def empty-trunk
  {:segments []
   :branch-sites []
   :tip nil})

(def empty-crown
  {:branches []
   :foliage []
   :tips []})

(def empty-tree
  {:trunk empty-trunk
   :crown empty-crown})

;; Individual geometry values look like this:
;;
;; segment      {:start [x y] :end [x y] :depth 0}
;; branch site  {:position [x y] :fraction 0.5}
;; foliage      {:center [x y] :radius 3.0 :depth 6}
;; tip          {:position [x y] :depth 6}

(def ^:private crown-seed-mask 0xC0FFEE)

(defn- make-segment
  [start end depth]
  {:start start
   :end end
   :depth depth})

(defn- make-branch-site
  [position fraction]
  {:position position
   :fraction fraction})

(defn- make-tip
  [position depth]
  {:position position
   :depth depth})

(defn- make-foliage
  [center radius depth]
  {:center center
   :radius radius
   :depth depth})

;; Random choices

(defn- random-unit
  "Return [updated-crown-state number-between-zero-and-one]."
  [crown-state]
  (let [[random-state number]
        (random/unit (:random-state crown-state))]
    [(assoc crown-state :random-state random-state) number]))

(defn- random-between
  "Return [updated-crown-state number-between-low-and-high]."
  [crown-state low high]
  (let [[random-state number]
        (random/between (:random-state crown-state) low high)]
    [(assoc crown-state :random-state random-state) number]))

;; Trunk calculation

(defn- calculate-trunk-height
  [{:keys [config style]} pot]
  (let [requested (* (:top-row pot) (:trunk-height-frac style))
        maximum (dec (double (:top-row pot)))
        minimum (get-in config [:tree :min-trunk-height])]
    (-> requested
        (min maximum)
        (max minimum))))

(defn- calculate-trunk-segments
  [{:keys [config]} height]
  (let [{:keys [trunk-segments-per-cell
                trunk-segments-min
                trunk-segments-max]} (:tree config)]
    (-> (* height trunk-segments-per-cell)
        int
        (max trunk-segments-min)
        (min trunk-segments-max))))

(defn- point-on-trunk
  "Return [next-random-state position] for one trunk sample."
  [{:keys [config style style-key]}
   random-state
   {:keys [center-x fraction height pot-row row]}]
  (case style-key
    :chokkan
    (let [[random-state wobble]
          (random/between random-state -0.3 0.3)]
      [random-state [(+ center-x wobble) row]])

    :moyogi
    [random-state
     [(+ center-x
         (* (:trunk-curve-amp style)
            (Math/sin (* 2.0 Math/PI
                         (:trunk-curve-waves style)
                         fraction))))
      row]]

    :shakan
    [random-state
     [(+ center-x (* (:trunk-lean style) fraction height))
      row]]

    :kengai
    (let [apex (get-in config [:tree :kengai-apex-frac])]
      (if (< fraction apex)
        [random-state [center-x row]]
        (let [cascade (/ (- fraction apex) (- 1.0 apex))]
          [random-state
           [(+ center-x
               (* 6.0 (Math/sin (* cascade Math/PI 0.6)) 1.5))
            (+ (- pot-row (* apex height))
               (* cascade 6.0))]])))

    ;; Bunjin / literati.
    [random-state
     [(+ center-x
         (* (:trunk-curve-amp style)
            (Math/sin (+ (* 2.0 Math/PI
                            (:trunk-curve-waves style)
                            fraction)
                         0.5))))
      row]]))

;; Crown calculation

(defn- turn
  [[x y] angle]
  (let [cosine (Math/cos angle)
        sine (Math/sin angle)]
    [(- (* x cosine) (* y sine))
     (+ (* x sine) (* y cosine))]))

(defn- end-of-branch
  [{:keys [config]} {:keys [start direction length]}]
  (let [[x y] start
        [direction-x direction-y] direction
        cell-aspect (get-in config [:tree :cell-aspect])]
    [(+ x (* direction-x length))
     (+ y (/ (* direction-y length) cell-aspect))]))

(defn- branch-complete?
  [{:keys [config style]} {:keys [length depth]}]
  (or (< length (get-in config [:tree :min-branch-length]))
      (> depth (:max-depth style))))

(defn- add-foliage-at-tip
  [crown-state {:keys [style]} {:keys [start depth]}]
  (let [[crown-state size] (random-unit crown-state)
        radius (* (:foliage-size style)
                  (+ 0.6 (* size 0.6)))]
    (-> crown-state
        (update-in [:crown :tips] conj
                   (make-tip start depth))
        (update-in [:crown :foliage] conj
                   (make-foliage start radius depth)))))

(defn- choose-child-count
  [crown-state {:keys [style]} depth]
  (let [[crown-state fork-chance] (random-unit crown-state)
        enough-depth? (< depth (dec (:max-depth style)))
        fork? (and enough-depth?
                   (< fork-chance (:branch-density style)))]
    [crown-state (if fork? 2 1)]))

(defn- choose-child-direction
  [crown-state direction depth child-number child-count]
  (let [forking? (= child-count 2)
        [crown-state bend-size]
        (if forking?
          (random-between crown-state 0.50 0.95)
          (random-between crown-state -0.40 0.40))
        side (if (zero? child-number) -1.0 1.0)
        bend (if forking? (* side bend-size) bend-size)
        [next-x raw-next-y] (turn direction bend)
        ;; Established branches gently seek upward.
        next-y (if (and (pos? depth) (> raw-next-y -0.1))
                 (- raw-next-y 0.2)
                 raw-next-y)]
    [crown-state [next-x next-y]]))

(declare grow-branch)

(defn- grow-children
  [crown-state settings branch child-count]
  (reduce
   (fn [crown-state child-number]
     (let [{:keys [direction length depth]} branch
           [crown-state direction]
           (choose-child-direction crown-state direction depth
                                   child-number child-count)
           child {:start (end-of-branch settings branch)
                  :direction direction
                  :length (* length
                             (get-in settings
                                     [:config :tree :branch-length-factor]))
                  :depth (inc depth)}]
       (grow-branch crown-state settings child)))
   crown-state
   (range child-count)))

(defn- grow-branch
  "Continue, fork, or finish one branch."
  [crown-state settings {:keys [start depth] :as branch}]
  (if (branch-complete? settings branch)
    (add-foliage-at-tip crown-state settings branch)
    (let [end (end-of-branch settings branch)
          crown-state
          (update-in crown-state [:crown :branches] conj
                     (make-segment start end depth))
          [crown-state child-count]
          (choose-child-count crown-state settings depth)]
      (grow-children crown-state settings branch child-count))))

(defn- inside-branch-area?
  [{:keys [config]} fraction]
  (let [[bottom top] (get-in config [:tree :branch-band])]
    (< bottom fraction top)))

(defn- make-side-branch
  [crown-state {:keys [style]} {:keys [position fraction]}]
  (let [[crown-state side-choice] (random-unit crown-state)
        [crown-state angle-size] (random-between crown-state 0.7 1.2)
        side (if (< side-choice 0.5) -1.0 1.0)
        angle (* side angle-size)]
    [crown-state
     {:start position
      :direction [(Math/sin angle)
                  (- (Math/abs (Math/cos angle)))]
      :length (* (:branch-length style)
                 (- 1.0 (* fraction 0.5)))
      :depth 1}]))

(defn- try-growing-side-branch
  [crown-state settings branch-site]
  (if-not (inside-branch-area? settings (:fraction branch-site))
    crown-state
    (let [[crown-state chance] (random-unit crown-state)]
      (if (>= chance (get-in settings [:style :branch-density]))
        crown-state
        (let [[crown-state branch]
              (make-side-branch crown-state settings branch-site)]
          (grow-branch crown-state settings branch))))))

(defn- add-top-foliage
  [crown-state {:keys [style style-key]} position]
  (let [[x y] position
        cascade? (= :kengai style-key)
        center [x (if cascade? y (- y 0.5))]
        radius (* (:foliage-size style)
                  (if cascade? 1.2 1.4))
        depth (:max-depth style)]
    (-> crown-state
        (update-in [:crown :tips] conj
                   (make-tip position depth))
        (update-in [:crown :foliage] conj
                   (make-foliage center radius depth)))))

;; Public calculation story

(defn calculate-pot
  "Calculate where the pot meets the tree."
  [{:keys [config]}]
  (let [rows (config/grid-height config)
        pot-height (if (>= rows 18) 4 3)
        preferred-top (- rows pot-height 1)]
    {:top-row (if (< preferred-top 5)
                (dec rows)
                preferred-top)}))

(defn calculate-trunk
  "Calculate the main trunk and its possible branch sites."
  [{:keys [config seed] :as settings} pot]
  (let [height (calculate-trunk-height settings pot)
        segment-count (calculate-trunk-segments settings height)
        center-x (* 0.5 (config/grid-width config))
        pot-row (:top-row pot)
        base [center-x (double pot-row)]
        first-trunk (assoc empty-trunk
                           :branch-sites [(make-branch-site base 0.0)]
                           :tip base)]
    (loop [point-number 1
           random-state (random/u32 seed)
           trunk first-trunk]
      (if (> point-number segment-count)
        trunk
        (let [fraction (/ (double point-number) segment-count)
              row (- pot-row (* fraction height))
              sample {:center-x center-x
                      :fraction fraction
                      :height height
                      :pot-row pot-row
                      :row row}
              [random-state point]
              (point-on-trunk settings random-state sample)
              previous (:tip trunk)
              trunk (-> trunk
                        (update :segments conj
                                (make-segment previous point 0))
                        (update :branch-sites conj
                                (make-branch-site point fraction))
                        (assoc :tip point))]
          (recur (inc point-number) random-state trunk))))))

(defn calculate-crown
  "Grow branches and foliage from the trunk branch sites."
  [{:keys [seed] :as settings} trunk]
  (let [first-state
        {:random-state (random/u32
                        (bit-xor (long seed) crown-seed-mask))
         :crown empty-crown}
        with-branches
        (reduce #(try-growing-side-branch %1 settings %2)
                first-state
                (:branch-sites trunk))
        complete-crown
        (add-top-foliage with-branches settings (:tip trunk))]
    (:crown complete-crown)))
