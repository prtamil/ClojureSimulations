;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.field
  "Own scalar-potential and curl calculations.

  WHY: particles and three static views query the same deterministic field
  mathematics. Keeping that mathematics here prevents the world lifecycle
  from being buried under noise formulas.

  DATA: a field is {:time seconds :permutation [...]}.
  OPERATIONS: advance time, sample potential, velocity, direction, or a grid.
  BOUNDARY: this namespace does not own particles, trails, controls, or
  rendering."
  (:require
   [sim.model.config :as config]
   [sim.utility.noise :as noise]))

;; Scalar sample {:position [x y] :intensity 0.5 :band 2}
;; Vector sample {:position [x y] :intensity 0.5 :band 2
;;                :velocity [vx vy]}

(defn create [permutation]
  {:time 0.0
   :permutation permutation})

(defn advance
  "Move a field forward by elapsed seconds at the configured drift rate."
  [simulation-config field drift-multiplier seconds]
  (update field :time + (* (get-in simulation-config [:field :drift-rate])
                           drift-multiplier
                           seconds)))

(defn potential
  "Sample the scalar potential at a simulation-grid position."
  [simulation-config field position warped?]
  (let [[x y] position
        scale (get-in simulation-config [:field :noise-scale])
        permutation (:permutation field)
        time (:time field)]
    (if warped?
      (noise/warped-potential-at simulation-config permutation
                                 (* x scale) (* y scale) time)
      (noise/potential-at simulation-config permutation
                          (* x scale) (* y scale) time))))

(defn velocity
  "Sample the curl velocity at a position in simulation-grid coordinates."
  [simulation-config field [x y] warped?]
  (let [scale (get-in simulation-config [:field :noise-scale])]
    (noise/curl-at simulation-config
                   (:permutation field)
                   (* x scale)
                   (* y scale)
                   (:time field)
                   warped?)))

(defn direction
  "Return a unit curl direction, preserving near-stationary samples."
  [simulation-config field position warped?]
  (let [[vx vy :as sampled] (velocity simulation-config field position warped?)
        magnitude (Math/hypot vx vy)
        epsilon (get-in simulation-config [:curl :velocity-epsilon])]
    (if (> magnitude epsilon)
      [(/ vx magnitude) (/ vy magnitude)]
      sampled)))

(defn- clamp [value low high]
  (max low (min high value)))

(defn- intensity-band [simulation-config intensity]
  (let [band-count (get-in simulation-config [:render :band-count])]
    (-> (* intensity band-count)
        int
        (clamp 0 (dec band-count)))))

(defn- make-sample [simulation-config position intensity]
  {:position position
   :intensity intensity
   :band (intensity-band simulation-config intensity)})

(defn- scalar-sample [simulation-config field pattern position]
  (case pattern
    :potential
    (let [midpoint (get-in simulation-config [:render :potential :midpoint])
          value-range (get-in simulation-config [:render :potential :range])
          intensity (clamp (+ midpoint
                              (* value-range
                                 (potential simulation-config field
                                            position false)))
                           0.0 1.0)]
      (make-sample simulation-config position intensity))

    :curl-magnitude
    (let [[vx vy] (velocity simulation-config field position false)
          gain (get-in simulation-config [:views :curl-magnitude :gain])
          intensity (clamp (* gain (Math/hypot vx vy)) 0.0 1.0)]
      (make-sample simulation-config position intensity))

    nil))

(defn- vector-sample [simulation-config field [x y :as position]]
  (let [[lattice-x lattice-y] (get-in simulation-config
                                       [:views :vector :lattice])]
    (when (and (zero? (mod x lattice-x))
               (zero? (mod y lattice-y)))
      (let [[vx vy :as sampled-velocity]
            (velocity simulation-config field position false)
            gain (get-in simulation-config [:views :curl-magnitude :gain])
            intensity (clamp (* gain (Math/hypot vx vy)) 0.0 1.0)]
        (assoc (make-sample simulation-config position intensity)
               :velocity sampled-velocity)))))

(defn sample-grid
  "Sample a static field view into values keyed by their grid position."
  [simulation-config field pattern]
  (let [width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)
        sample-at (if (= pattern :vector)
                    #(vector-sample simulation-config field %)
                    #(scalar-sample simulation-config field pattern %))]
    (into {}
          (keep (fn [position]
                  (when-let [sample (sample-at position)]
                    [position sample])))
          (for [y (range height)
                x (range width)]
            [x y]))))
