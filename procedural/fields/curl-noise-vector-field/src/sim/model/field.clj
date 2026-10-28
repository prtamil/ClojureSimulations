;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.field
  "Scalar-potential and curl-field operations over immutable field values."
  (:require
   [sim.model.config :as config]
   [sim.utility.noise :as noise]))

(defn create [permutation]
  {:time 0.0 :permutation permutation})

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

(defn- band-for [simulation-config intensity]
  (let [band-count (get-in simulation-config [:render :band-count])]
    (-> (* intensity band-count)
        int
        (clamp 0 (dec band-count)))))

(defn- scalar-cell [simulation-config field pattern position]
  (case pattern
    :potential
    (let [midpoint (get-in simulation-config [:render :potential :midpoint])
          value-range (get-in simulation-config [:render :potential :range])
          intensity (clamp (+ midpoint
                              (* value-range
                                 (potential simulation-config field
                                            position false)))
                           0.0 1.0)]
      {:position position
       :intensity intensity
       :band (band-for simulation-config intensity)})

    :curl-magnitude
    (let [[vx vy] (velocity simulation-config field position false)
          gain (get-in simulation-config [:views :curl-magnitude :gain])
          intensity (clamp (* gain (Math/hypot vx vy)) 0.0 1.0)]
      {:position position
       :intensity intensity
       :band (band-for simulation-config intensity)})

    nil))

(defn- vector-cell [simulation-config field [x y :as position]]
  (let [[lattice-x lattice-y] (get-in simulation-config
                                       [:views :vector :lattice])]
    (when (and (zero? (mod x lattice-x))
               (zero? (mod y lattice-y)))
      (let [[vx vy :as sampled-velocity]
            (velocity simulation-config field position false)
            gain (get-in simulation-config [:views :curl-magnitude :gain])
            intensity (clamp (* gain (Math/hypot vx vy)) 0.0 1.0)]
        {:position position
         :intensity intensity
         :band (band-for simulation-config intensity)
         :velocity sampled-velocity}))))

(defn sample-grid
  "Sample a static view into semantic cells keyed by [x y]."
  [simulation-config field pattern]
  (let [width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)
        sample (if (= pattern :vector)
                 #(vector-cell simulation-config field %)
                 #(scalar-cell simulation-config field pattern %))]
    (into {}
          (keep (fn [position]
                  (when-let [cell (sample position)]
                    [position cell])))
          (for [y (range height)
                x (range width)]
            [x y]))))
