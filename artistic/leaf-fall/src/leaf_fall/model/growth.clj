;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.model.growth
  "Calculate renderer-independent trunk and crown geometry.

  WHY: seeded trunk sampling and recursive branch growth are substantial
  continuous-geometry rules independent of a character grid.
  DATA: empty-tree documents persistent geometry; smaller shapes appear below.
  OPERATIONS: calculate the trunk and crown.
  BOUNDARY: world phases, raster cells, falling motion, and drawing stay out."
  (:require
   [leaf-fall.model.config :as config]
   [leaf-fall.utility.random :as random]))

;; Tree data

(def empty-trunk
  {:base nil
   :top nil
   :height 0
   :segments []
   :marks []})

(def empty-crown
  {:branches []
   :foliage []})

(def empty-tree
  {:trunk empty-trunk
   :crown empty-crown})

;; Individual geometry values look like this:
;;
;; segment  {:start [row column] :end [row column] :depth 0}
;; mark     {:position [row column] :glyph \/}
;; foliage  {:center [row column] :radius 3}

(defn- make-segment
  [start end depth]
  {:start start
   :end end
   :depth depth})

(defn- make-mark
  [position glyph]
  {:position position
   :glyph glyph})

(defn- make-foliage
  [center radius]
  {:center center
   :radius (max 1 radius)})

;; Trunk calculation

(defn- choose-trunk-height
  [simulation-config seed base-row]
  (let [[_ height-choice] (random/unit (random/u32 seed))
        minimum (get-in simulation-config [:tree :trunk-height-min])
        maximum (get-in simulation-config [:tree :trunk-height-max])
        requested (int (* (+ minimum
                             (* height-choice (- maximum minimum)))
                          (config/grid-height simulation-config)))
        top-row (max 2 (- base-row (max 8 requested)))]
    (- base-row top-row)))

(defn- make-trunk-segments
  [[base-row base-column] [top-row _] height]
  (let [flare-height (quot height 4)]
    [(make-segment [base-row base-column]
                   [top-row base-column]
                   0)
     (make-segment [base-row (inc base-column)]
                   [top-row (inc base-column)]
                   0)
     (make-segment [base-row (dec base-column)]
                   [(- base-row flare-height) (dec base-column)]
                   0)
     (make-segment [base-row (+ base-column 2)]
                   [(- base-row flare-height) (+ base-column 2)]
                   0)]))

(defn- make-trunk-marks
  [[base-row base-column]]
  [(make-mark [base-row (- base-column 2)] \/)
   (make-mark [base-row (+ base-column 3)] \\)])

;; Crown calculation

(defn- random-state-after-height
  [seed]
  (first (random/unit (random/u32 seed))))

(defn- end-of-branch
  [{:keys [start angle length]}]
  (let [[row column] start]
    [(- row (int (* (Math/sin angle) length)))
     (+ column (int (* (Math/cos angle) length)))]))

(defn- add-branch
  [crown-state start end depth]
  (update-in crown-state [:crown :branches] conj
             (make-segment start end depth)))

(defn- add-foliage
  [crown-state center radius]
  (update-in crown-state [:crown :foliage] conj
             (make-foliage center radius)))

(declare grow-branch)

(defn- grow-children
  [crown-state children settings]
  (reduce #(grow-branch %1 %2 settings)
          crown-state
          children))

(defn- grow-branch
  "Continue, fork, or finish one branch."
  [crown-state {:keys [start angle length depth] :as branch} settings]
  (let [maximum-depth (get-in settings [:config :tree :max-depth])]
    (if (or (<= length 1) (> depth maximum-depth))
      (add-foliage crown-state
                   start
                   (-> (+ 2 (quot (- maximum-depth depth) 2))
                       (max 1)
                       (min 5)))
      (let [end (end-of-branch branch)
            crown-state (add-branch crown-state start end depth)
            random-state (:random-state crown-state)
            [random-state sprinkle-chance]
            (if (>= depth 4)
              (random/unit random-state)
              [random-state 1.0])
            crown-state (assoc crown-state :random-state random-state)
            crown-state (if (< sprinkle-chance 0.40)
                          (add-foliage crown-state end 1)
                          crown-state)]
        (if (>= depth maximum-depth)
          (add-foliage crown-state end 3)
          (let [random-state (:random-state crown-state)
                [random-state spread-choice] (random/unit random-state)
                spread (+ 0.30 (* spread-choice 0.30))
                [random-state length-choice] (random/unit random-state)
                shorter-length
                (max 2 (int (* length
                               (+ 0.50 (* length-choice 0.20)))))
                child (fn [child-angle child-length]
                        {:start end
                         :angle child-angle
                         :length child-length
                         :depth (inc depth)})
                children [(child (+ angle spread) shorter-length)
                          (child (- angle spread) shorter-length)]
                [random-state third-branch-chance]
                (random/unit random-state)
                [random-state children]
                (if (< third-branch-chance 0.35)
                  (let [[random-state jitter]
                        (random/between random-state -0.1 0.1)]
                    [random-state
                     (conj children
                           (child (+ angle jitter)
                                  (int (* shorter-length 0.80))))])
                  [random-state children])
                crown-state (assoc crown-state
                                   :random-state random-state)]
            ;; Reverse order preserves the original depth-first painter order.
            (grow-children crown-state (reverse children) settings)))))))

(defn- make-side-branches
  [random-state trunk branch-spread]
  (let [[base-row base-column] (:base trunk)
        height (:height trunk)
        [random-state count-choice] (random/unit random-state)
        site-count (+ 3 (int (* count-choice 3.0)))
        upward (/ Math/PI 2.0)]
    (loop [site-number 0
           random-state random-state
           branches []]
      (if (= site-number site-count)
        [random-state branches]
        (let [height-fraction (/ (inc site-number)
                                 (double (inc site-count)))
              branch-row (- base-row
                            (int (* height-fraction height 0.95)))
              [random-state spread-choice] (random/unit random-state)
              spread (+ branch-spread (* spread-choice 0.35))
              [random-state length-choice] (random/unit random-state)
              length-factor
              (+ 0.25
                 (* 0.35
                    (- 1.0
                       (* (Math/abs (- height-fraction 0.5)) 2.0)))
                 (* length-choice 0.10))
              branch-length (max 3 (int (* length-factor height)))
              branches
              (conj branches
                    {:start [branch-row base-column]
                     :angle (+ upward spread)
                     :length branch-length
                     :depth 1}
                    {:start [branch-row (inc base-column)]
                     :angle (- upward spread)
                     :length branch-length
                     :depth 1})]
          (recur (inc site-number) random-state branches))))))

(defn- make-top-branches
  [random-state trunk]
  (let [[top-row top-column] (:top trunk)
        height (:height trunk)
        [random-state spread-choice] (random/unit random-state)
        top-length (max 3 (quot height 3))
        spread (+ 0.55 (* spread-choice 0.30))
        upward (/ Math/PI 2.0)]
    [random-state
     [{:start [top-row top-column]
       :angle (+ upward spread)
       :length top-length
       :depth 1}
      {:start [top-row (inc top-column)]
       :angle (- upward spread)
       :length top-length
       :depth 1}
      {:start [top-row top-column]
       :angle upward
       :length (int (* top-length 0.8))
       :depth 1}]]))

;; Public calculation story

(defn calculate-trunk
  "Calculate the tree base, trunk segments, and root marks."
  [{:keys [config seed]}]
  (let [base-row (- (config/grid-height config) 2)
        base-column (dec (quot (config/grid-width config) 2))
        base [base-row base-column]
        height (choose-trunk-height config seed base-row)
        top [(- base-row height) base-column]]
    {:base base
     :top top
     :height height
     :segments (make-trunk-segments base top height)
     :marks (make-trunk-marks base)}))

(defn calculate-crown
  "Grow recursive branches and foliage from a calculated trunk."
  [{:keys [config seed] :as settings} trunk]
  (let [first-random-state (random-state-after-height seed)
        [random-state side-branches]
        (make-side-branches first-random-state
                            trunk
                            (get-in config [:tree :branch-spread]))
        [random-state top-branches]
        (make-top-branches random-state trunk)
        first-crown-state
        {:random-state random-state
         :crown empty-crown}
        complete-crown
        (grow-children first-crown-state
                       (reverse (into side-branches top-branches))
                       settings)]
    (:crown complete-crown)))
