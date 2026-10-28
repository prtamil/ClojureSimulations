;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.model.growth
  "Seeded generation of renderer-independent bonsai geometry."
  (:require
   [bonsai.model.config :as config]
   [bonsai.utility.random :as random]))

(defn- add-segment [tree segment]
  (update tree :segments conj segment))

(defn- add-foliage [tree foliage]
  (update tree :foliage conj foliage))

(defn- add-tip [tree tip]
  (update tree :tips conj tip))

(defn- rotate-direction [[x y] angle]
  (let [cosine (Math/cos angle)
        sine (Math/sin angle)]
    [(- (* x cosine) (* y sine))
     (+ (* x sine) (* y cosine))]))

(declare grow-branch)

(defn- grow-children
  [tree rng origin direction length depth style simulation-config child-count]
  (loop [child-number 0
         tree tree
         rng rng]
    (if (= child-number child-count)
      [tree rng]
      (let [[rng bend-size]
            (if (= child-count 1)
              (random/between rng -0.40 0.40)
              (random/between rng 0.50 0.95))
            bend (if (= child-count 1)
                   bend-size
                   (* (if (zero? child-number) -1.0 1.0) bend-size))
            [next-x raw-next-y] (rotate-direction direction bend)
            ;; Side branches gently seek upward instead of crossing downward.
            next-y (if (and (pos? depth) (> raw-next-y -0.1))
                     (- raw-next-y 0.2)
                     raw-next-y)
            next-length (* length
                           (get-in simulation-config
                                   [:tree :branch-length-factor]))
            [tree rng]
            (grow-branch tree rng origin [next-x next-y] next-length
                         (inc depth) style simulation-config)]
        (recur (inc child-number) tree rng)))))

(defn- grow-branch
  "Grow one branch and recursively continue or fork at its endpoint."
  [tree rng [x y] [direction-x direction-y :as direction]
   length depth style simulation-config]
  (let [{:keys [cell-aspect min-branch-length]} (:tree simulation-config)
        max-depth (:max-depth style)]
    (if (or (< length min-branch-length) (> depth max-depth))
      (let [tree (add-tip tree {:x x :y y :depth depth})
            [rng radius-scale] (random/unit rng)
            radius (* (:foliage-size style)
                      (+ 0.6 (* radius-scale 0.6)))]
        [(add-foliage tree
                      {:cx x :cy y :radius radius :depth-at-tip depth})
         rng])
      (let [end [(+ x (* direction-x length))
                 (+ y (/ (* direction-y length) cell-aspect))]
            tree (add-segment tree
                              {:x0 x :y0 y
                               :x1 (first end) :y1 (second end)
                               :depth depth})
            [rng fork-roll] (random/unit rng)
            child-count (if (and (< fork-roll (:branch-density style))
                                 (< depth (dec max-depth)))
                          2
                          1)]
        (grow-children tree rng end direction length depth style
                       simulation-config child-count)))))

(defn- pot-top-row [row-count]
  (let [pot-height (if (>= row-count 18) 4 3)
        candidate (- row-count pot-height 1)]
    (if (< candidate 5) (dec row-count) candidate)))

(defn- trunk-point
  "Return [next-rng [x y]] for one point on the selected silhouette."
  [rng style-key style center-x fraction trunk-height pot-top y
   simulation-config]
  (case style-key
    :chokkan
    (let [[rng wobble] (random/between rng -0.3 0.3)]
      [rng [(+ center-x wobble) y]])

    :moyogi
    [rng [(+ center-x
             (* (:trunk-curve-amp style)
                (Math/sin (* 2.0 Math/PI
                             (:trunk-curve-waves style)
                             fraction))))
          y]]

    :shakan
    [rng [(+ center-x (* (:trunk-lean style) fraction trunk-height)) y]]

    :kengai
    (let [apex (get-in simulation-config [:tree :kengai-apex-frac])]
      (if (< fraction apex)
        [rng [center-x y]]
        (let [cascade-fraction (/ (- fraction apex) (- 1.0 apex))]
          [rng
           [(+ center-x
               (* 6.0 (Math/sin (* cascade-fraction Math/PI 0.6)) 1.5))
            (+ (- pot-top (* apex trunk-height)) (* cascade-fraction 6.0))]])))

    ;; Bunjin / literati.
    [rng [(+ center-x
             (* (:trunk-curve-amp style)
                (Math/sin (+ (* 2.0 Math/PI
                                (:trunk-curve-waves style)
                                fraction)
                             0.5))))
          y]]))

(defn- maybe-grow-side-branch
  [tree rng origin fraction style simulation-config]
  (let [[band-low band-high] (get-in simulation-config [:tree :branch-band])]
    (if (or (<= fraction band-low) (>= fraction band-high))
      [tree rng]
      (let [[rng sprout-roll] (random/unit rng)]
        (if (>= sprout-roll (:branch-density style))
          [tree rng]
          (let [[rng side-roll] (random/unit rng)
                [rng angle-size] (random/between rng 0.7 1.2)
                angle (* (if (< side-roll 0.5) -1.0 1.0) angle-size)
                direction [(Math/sin angle)
                           (- (Math/abs (Math/cos angle)))]
                length (* (:branch-length style)
                          (- 1.0 (* fraction 0.5)))]
            (grow-branch tree rng origin direction length 1 style
                         simulation-config)))))))

(defn- add-crown [tree style-key style [tip-x tip-y]]
  (-> tree
      (add-tip {:x tip-x :y tip-y :depth (:max-depth style)})
      (add-foliage
       {:cx tip-x
        :cy (if (= :kengai style-key) tip-y (- tip-y 0.5))
        :radius (* (:foliage-size style)
                   (if (= :kengai style-key) 1.2 1.4))
        :depth-at-tip (:max-depth style)})))

(defn grow-tree
  "Generate deterministic geometry from config, seed, and style.

  Theme is intentionally absent: seasons change presentation, not botany."
  [simulation-config seed style-key]
  (let [style (config/style style-key)
        column-count (config/grid-width simulation-config)
        row-count (config/grid-height simulation-config)
        pot-top (pot-top-row row-count)
        requested-height (* pot-top (:trunk-height-frac style))
        trunk-height (-> requested-height
                         (min (dec (double pot-top)))
                         (max (get-in simulation-config
                                      [:tree :min-trunk-height])))
        center-x (* column-count 0.5)
        segments-per-cell (get-in simulation-config
                                  [:tree :trunk-segments-per-cell])
        minimum-segments (get-in simulation-config [:tree :trunk-segments-min])
        maximum-segments (get-in simulation-config [:tree :trunk-segments-max])
        segment-count (-> (* trunk-height segments-per-cell)
                          int
                          (max minimum-segments)
                          (min maximum-segments))]
    (loop [point-number 1
           tree {:segments [] :foliage [] :tips []}
           rng (random/u32 seed)
           previous [center-x (double pot-top)]]
      (if (> point-number segment-count)
        {:tree (add-crown tree style-key style previous)
         :pot-top pot-top}
        (let [fraction (/ (double point-number) segment-count)
              y (- pot-top (* fraction trunk-height))
              [rng point] (trunk-point rng style-key style center-x fraction
                                       trunk-height pot-top y
                                       simulation-config)
              tree (add-segment tree
                                {:x0 (first previous) :y0 (second previous)
                                 :x1 (first point) :y1 (second point)
                                 :depth 0})
              [tree rng]
              (maybe-grow-side-branch tree rng point fraction style
                                      simulation-config)]
          (recur (inc point-number) tree rng point))))))
