;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.model.growth
  "Seeded, renderer-independent geometry for the procedural tree."
  (:require
   [leaf-fall.model.config :as config]
   [leaf-fall.utility.random :as random]))

(defn- endpoint [{:keys [origin angle length]}]
  (let [[row column] origin]
    [(- row (int (* (Math/sin angle) length)))
     (+ column (int (* (Math/cos angle) length)))]))

(defn- add-segment [tree origin destination role depth]
  (update tree :segments conj
          {:from origin :to destination :role role :depth depth}))

(defn- add-foliage [tree center radius]
  (update tree :foliage conj {:center center :radius (max 1 radius)}))

(declare grow-branch)

(defn- grow-children [tree rng children simulation-config]
  (reduce
   (fn [[tree rng] child]
     (grow-branch tree rng child simulation-config))
   [tree rng]
   children))

(defn- grow-branch
  [tree rng {:keys [origin angle length depth] :as branch} simulation-config]
  (let [max-depth (get-in simulation-config [:tree :max-depth])]
    (if (or (<= length 1) (> depth max-depth))
      [(add-foliage tree origin
                    (-> (+ 2 (quot (- max-depth depth) 2))
                        (max 1)
                        (min 5)))
       rng]
      (let [destination (endpoint branch)
            role (if (<= depth 2) :trunk :branch)
            tree (add-segment tree origin destination role depth)
            [rng sprinkle-roll] (if (>= depth 4)
                                  (random/unit rng)
                                  [rng 1.0])
            tree (if (< sprinkle-roll 0.40)
                   (add-foliage tree destination 1)
                   tree)]
        (if (>= depth max-depth)
          [(add-foliage tree destination 3) rng]
          (let [[rng spread-roll] (random/unit rng)
                spread (+ 0.30 (* spread-roll 0.30))
                [rng length-roll] (random/unit rng)
                smaller-length (max 2 (int (* length
                                               (+ 0.50 (* length-roll 0.20)))))
                child (fn [child-angle child-length]
                        {:origin destination
                         :angle child-angle
                         :length child-length
                         :depth (inc depth)})
                children [(child (+ angle spread) smaller-length)
                          (child (- angle spread) smaller-length)]
                [rng third-roll] (random/unit rng)
                [rng children]
                (if (< third-roll 0.35)
                  (let [[rng jitter] (random/between rng -0.1 0.1)]
                    [rng (conj children
                               (child (+ angle jitter)
                                      (int (* smaller-length 0.80))))])
                  [rng children])]
            ;; Reverse order matches a natural depth-first painter: the last
            ;; conceptual child grows first without an explicit branch stack.
            (grow-children tree rng (reverse children)
                           simulation-config)))))))

(defn- trunk-geometry [base-row base-column trunk-top trunk-height]
  (let [flare (quot trunk-height 4)]
    {:segments
     [{:from [base-row base-column] :to [trunk-top base-column]
       :role :trunk :depth 0}
      {:from [base-row (inc base-column)] :to [trunk-top (inc base-column)]
       :role :trunk :depth 0}
      {:from [base-row (dec base-column)]
       :to [(- base-row flare) (dec base-column)]
       :role :trunk :depth 0}
      {:from [base-row (+ base-column 2)]
       :to [(- base-row flare) (+ base-column 2)]
       :role :trunk :depth 0}]
     :marks [{:position [base-row (- base-column 2)]
              :glyph \/ :role :trunk}
             {:position [base-row (+ base-column 3)]
              :glyph \\ :role :trunk}]
     :foliage []}))

(defn- trunk-branches [rng base-row base-column trunk-height branch-spread]
  (let [[rng count-roll] (random/unit rng)
        point-count (+ 3 (int (* count-roll 3.0)))
        up (/ Math/PI 2.0)]
    (loop [index 0
           rng rng
           branches []]
      (if (= index point-count)
        [rng branches]
        (let [height-fraction (/ (inc index) (double (inc point-count)))
              branch-row (- base-row
                            (int (* height-fraction trunk-height 0.95)))
              [rng spread-roll] (random/unit rng)
              spread (+ branch-spread (* spread-roll 0.35))
              [rng length-roll] (random/unit rng)
              length-factor (+ 0.25
                               (* 0.35
                                  (- 1.0
                                     (* (Math/abs (- height-fraction 0.5)) 2.0)))
                               (* length-roll 0.10))
              branch-length (max 3 (int (* length-factor trunk-height)))
              branches
              (conj branches
                    {:origin [branch-row base-column]
                     :angle (+ up spread) :length branch-length :depth 1}
                    {:origin [branch-row (inc base-column)]
                     :angle (- up spread) :length branch-length :depth 1})]
          (recur (inc index) rng branches))))))

(defn- crown-branches [rng base-column trunk-top trunk-height]
  (let [[rng spread-roll] (random/unit rng)
        top-length (max 3 (quot trunk-height 3))
        spread (+ 0.55 (* spread-roll 0.30))
        up (/ Math/PI 2.0)]
    [rng [{:origin [trunk-top base-column]
           :angle (+ up spread) :length top-length :depth 1}
          {:origin [trunk-top (inc base-column)]
           :angle (- up spread) :length top-length :depth 1}
          {:origin [trunk-top base-column]
           :angle up :length (int (* top-length 0.8)) :depth 1}]]))

(defn grow-tree
  "Generate deterministic branch and foliage geometry for config and seed."
  [simulation-config seed]
  (let [simulation-config (config/validate! simulation-config)
        rows (config/grid-height simulation-config)
        columns (config/grid-width simulation-config)
        base-row (- rows 2)
        base-column (dec (quot columns 2))
        [rng height-roll] (random/unit (random/u32 seed))
        minimum (get-in simulation-config [:tree :trunk-height-min])
        maximum (get-in simulation-config [:tree :trunk-height-max])
        trunk-height (max 8 (int (* (+ minimum
                                      (* height-roll (- maximum minimum)))
                                   rows)))
        trunk-top (max 2 (- base-row trunk-height))
        trunk-height (- base-row trunk-top)
        tree (trunk-geometry base-row base-column trunk-top trunk-height)
        [rng side-branches]
        (trunk-branches rng base-row base-column trunk-height
                        (get-in simulation-config [:tree :branch-spread]))
        [rng crown] (crown-branches rng base-column trunk-top trunk-height)
        [tree _]
        (grow-children tree rng (reverse (into side-branches crown))
                       simulation-config)]
    tree))
