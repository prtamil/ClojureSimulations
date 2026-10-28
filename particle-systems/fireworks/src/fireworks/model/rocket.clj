;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.model.rocket
  "A rocket is one immutable value in one of three phases.

  :waiting   {:delay-seconds ...}
  :ascending {:position [x y] :velocity [vx vy] :color-index ...}
  :bursting  {:sparks [...]}."
  (:require
   [fireworks.model.config :as config]
   [fireworks.model.spark :as spark]
   [fireworks.utility.random :as random]))

(defn waiting [id delay-seconds]
  {:id id
   :phase :waiting
   :delay-seconds (double delay-seconds)})

(defn initial-rockets [simulation-config rocket-count]
  (let [interval (get-in simulation-config
                         [:rockets :initial-launch-interval-seconds])]
    (mapv #(waiting % (* % interval)) (range rocket-count))))

(defn- launch [simulation-config rng {:keys [id]}]
  (let [width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)
        launch-speeds (get-in simulation-config [:rockets :launch-speeds])
        [rng column] (random/index rng width)
        [rng speed-index] (random/index rng (count launch-speeds))
        [rng color-index] (random/index rng 7)]
    [rng {:id id
          :phase :ascending
          :position [(double column) (double (dec height))]
          :velocity [0.0 (- (nth launch-speeds speed-index))]
          :color-index color-index}]))

(defn- advance-waiting [simulation-config rng dt rocket]
  (let [remaining (- (:delay-seconds rocket) dt)]
    (if (pos? remaining)
      [rng (assoc rocket :delay-seconds remaining)]
      (launch simulation-config rng rocket))))

(defn- advance-ascending [simulation-config rng dt rocket]
  (let [[x y] (:position rocket)
        [vx vy] (:velocity rocket)
        acceleration (get-in simulation-config
                             [:rockets :upward-acceleration])
        next-position [(+ x (* vx dt)) (+ y (* vy dt))]
        next-velocity [vx (+ vy (* acceleration dt))]
        burst-ceiling (get-in simulation-config [:rockets :burst-ceiling])]
    (if (or (not (neg? (second next-velocity)))
            (< (second next-position) burst-ceiling))
      (let [[rng sparks] (spark/burst simulation-config rng next-position)]
        [rng {:id (:id rocket) :phase :bursting :sparks sparks}])
      [rng (assoc rocket
                  :position next-position
                  :velocity next-velocity)])))

(defn- advance-bursting [simulation-config rng dt rocket]
  (let [sparks (spark/advance-all simulation-config dt (:sparks rocket))]
    (if (seq sparks)
      [rng (assoc rocket :sparks sparks)]
      (let [delay-min (get-in simulation-config
                              [:rockets :relaunch-delay-seconds :min])
            delay-max (get-in simulation-config
                              [:rockets :relaunch-delay-seconds :max])
            [rng delay] (random/between rng delay-min delay-max)]
        [rng (waiting (:id rocket) delay)]))))

(defn advance
  "Advance a rocket and return [next-rng next-rocket]."
  [simulation-config rng dt rocket]
  (case (:phase rocket)
    :waiting (advance-waiting simulation-config rng dt rocket)
    :ascending (advance-ascending simulation-config rng dt rocket)
    :bursting (advance-bursting simulation-config rng dt rocket)
    (throw (ex-info "Unknown rocket phase"
                    {:rocket-id (:id rocket) :phase (:phase rocket)}))))
