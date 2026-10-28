;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.particle
  "Creation and elapsed-time advection of immutable particles."
  (:require
   [sim.model.config :as config]
   [sim.model.field :as field]
   [sim.utility.random :as random]))

(defn spawn
  [simulation-config rng-state]
  (let [width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)
        band-count (get-in simulation-config [:render :band-count])
        {:keys [min max]} (get-in simulation-config
                                   [:particles :lifetime-seconds])
        [rng-state x] (random/between rng-state 0.0 (double width))
        [rng-state y] (random/between rng-state 0.0 (double height))
        [rng-state band] (random/next-int rng-state band-count)
        [rng-state lifetime] (random/between rng-state min max)]
    [rng-state {:position [x y]
                :band band
                :age-seconds 0.0
                :lifetime-seconds lifetime}]))

(defn spawn-many [simulation-config rng-state]
  (loop [remaining (get-in simulation-config [:particles :count])
         rng-state rng-state
         particles []]
    (if (zero? remaining)
      [rng-state particles]
      (let [[rng-state particle] (spawn simulation-config rng-state)]
        (recur (dec remaining) rng-state (conj particles particle))))))

(defn in-bounds? [simulation-config [x y]]
  (and (<= 0.0 x) (< x (config/grid-width simulation-config))
       (<= 0.0 y) (< y (config/grid-height simulation-config))))

(defn advance
  [simulation-config field-value speed warped? seconds particle]
  (let [[x y] (:position particle)
        [dx dy] (field/direction simulation-config field-value
                                 (:position particle) warped?)]
    (-> particle
        (assoc :position [(+ x (* dx speed seconds))
                          (+ y (* dy speed seconds))])
        (update :age-seconds + seconds))))

(defn expired? [simulation-config particle]
  (or (>= (:age-seconds particle) (:lifetime-seconds particle))
      (not (in-bounds? simulation-config (:position particle)))))

(defn advance-all
  "Advance every particle, returning particles, deposits, and the RNG state."
  [simulation-config field-value speed warped? seconds rng-state particles]
  (reduce
   (fn [{:keys [rng-state] :as result} particle]
     (let [moved (advance simulation-config field-value speed warped?
                          seconds particle)
           visible? (in-bounds? simulation-config (:position moved))
           result (if visible?
                    (update result :deposits conj
                            {:position (mapv int (:position moved))
                             :band (:band moved)})
                    result)
           [rng-state particle]
           (if (expired? simulation-config moved)
             (spawn simulation-config rng-state)
             [rng-state moved])]
       (-> result
           (assoc :rng-state rng-state)
           (update :particles conj particle))))
   {:rng-state rng-state :particles [] :deposits []}
   particles))
