;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.model.firework
  "Own the waiting, flying, and bursting stages of one firework.

  WHY: stage transitions protect the invariant that each stage has exactly the
  data its behavior needs. DATA: the three short stage shapes appear below.
  OPERATIONS: create waiting fireworks and advance one/all stage machines.
  BOUNDARY: world clocks, show controls, spark physics, and rendering stay out."
  (:require
   [fireworks.model.config :as config]
   [fireworks.model.spark :as spark]
   [fireworks.utility.random :as random]))

;; A firework has one of three visible shapes:
;;
;; waiting   {:id 0 :stage :waiting :delay-seconds 0.5}
;; flying    {:id 0 :stage :flying :position [x y] :velocity [vx vy]
;;            :color-index 0}
;; bursting  {:id 0 :stage :bursting :sparks [spark ...]}

(defn wait-before-launch [id delay-seconds]
  {:id id
   :stage :waiting
   :delay-seconds (double delay-seconds)})

(defn make-fireworks [simulation-config firework-count]
  (let [launch-interval (get-in simulation-config
                                [:rockets :initial-launch-interval-seconds])]
    (mapv #(wait-before-launch % (* % launch-interval))
          (range firework-count))))

(defn- launch-firework [simulation-config random-state firework]
  (let [width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)
        launch-speeds (get-in simulation-config [:rockets :launch-speeds])
        [random-state column] (random/index random-state width)
        [random-state speed-index] (random/index random-state
                                                 (count launch-speeds))
        [random-state color-index] (random/index random-state 7)]
    [random-state
     {:id (:id firework)
      :stage :flying
      :position [(double column) (double (dec height))]
      :velocity [0.0 (- (nth launch-speeds speed-index))]
      :color-index color-index}]))

(defn- wait-or-launch [simulation-config random-state dt firework]
  (let [remaining (- (:delay-seconds firework) dt)]
    (if (pos? remaining)
      [random-state (assoc firework :delay-seconds remaining)]
      (launch-firework simulation-config random-state firework))))

(defn- fly-or-burst [simulation-config random-state dt firework]
  (let [[x y] (:position firework)
        [vx vy] (:velocity firework)
        acceleration (get-in simulation-config
                             [:rockets :upward-acceleration])
        next-position [(+ x (* vx dt)) (+ y (* vy dt))]
        next-velocity [vx (+ vy (* acceleration dt))]
        burst-ceiling (get-in simulation-config [:rockets :burst-ceiling])]
    (if (or (not (neg? (second next-velocity)))
            (< (second next-position) burst-ceiling))
      (let [[random-state sparks]
            (spark/make-burst simulation-config random-state next-position)]
        [random-state
         {:id (:id firework)
          :stage :bursting
          :sparks sparks}])
      [random-state
       (assoc firework
              :position next-position
              :velocity next-velocity)])))

(defn- burst-or-reload [simulation-config random-state dt firework]
  (let [sparks (spark/move-sparks simulation-config dt (:sparks firework))]
    (if (seq sparks)
      [random-state (assoc firework :sparks sparks)]
      (let [delay-min (get-in simulation-config
                              [:rockets :relaunch-delay-seconds :min])
            delay-max (get-in simulation-config
                              [:rockets :relaunch-delay-seconds :max])
            [random-state delay]
            (random/between random-state delay-min delay-max)]
        [random-state (wait-before-launch (:id firework) delay)]))))

(defn advance-firework
  "Move one firework to its next stage and return [random-state firework]."
  [simulation-config random-state dt firework]
  (case (:stage firework)
    :waiting (wait-or-launch simulation-config random-state dt firework)
    :flying (fly-or-burst simulation-config random-state dt firework)
    :bursting (burst-or-reload simulation-config random-state dt firework)
    (throw (ex-info "Unknown firework stage"
                    {:firework-id (:id firework)
                     :stage (:stage firework)}))))

(defn advance-fireworks
  "Advance every firework from left to right through one simulation step."
  [simulation-config random-state dt fireworks]
  (reduce
   (fn [[random-state advanced] firework]
     (let [[random-state next-firework]
           (advance-firework simulation-config random-state dt firework)]
       [random-state (conj advanced next-firework)]))
   [random-state []]
   fireworks))
