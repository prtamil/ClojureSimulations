;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.drop
  "Own deterministic construction and evolution of rain streams.

  WHY: spawning, glyph mutation probability, respawn, and explicit random
  state form a substantial stream lifecycle that would obscure the world flow.
  DATA: a drop is {:id :column :head-y :speed :glyphs}; random state is passed
  in and returned rather than retained here.
  OPERATIONS: spawn one/many streams and advance one/all streams.
  BOUNDARY: world clocks, controls, visibility, and presentation remain out."
  (:require
   [sim.model.config :as config]
   [sim.utility.random :as random]))

(defn- between [state low high]
  (let [[state sample] (random/next-double state)]
    [state (+ (double low) (* sample (- (double high) (double low))))]))

(defn- random-glyph [simulation-config state]
  (let [charset (vec (get-in simulation-config [:rain :charset]))
        [state index] (random/next-int state (count charset))]
    [state (nth charset index)]))

(defn- random-glyphs [simulation-config state length]
  (reduce (fn [[state glyphs] _]
            (let [[state glyph] (random-glyph simulation-config state)]
              [state (conj glyphs glyph)]))
          [state []]
          (range length)))

(defn spawn
  "Create one deterministic stream and return [next-state drop]."
  [simulation-config state id]
  (let [width (config/grid-width simulation-config)
        {length-min :min length-max :max} (get-in simulation-config
                                                   [:rain :length])
        {speed-min :min speed-max :max} (get-in simulation-config
                                                 [:rain :speed])
        spawn-depth (get-in simulation-config [:rain :spawn-depth])
        [state column] (random/next-int state width)
        [state row-offset] (random/next-int state spawn-depth)
        [state length-offset]
        (random/next-int state (inc (- length-max length-min)))
        length (+ length-min length-offset)
        [state speed] (between state speed-min speed-max)
        [state glyphs] (random-glyphs simulation-config state length)]
    [state {:id id
            :column column
            :head-y (double (- (inc row-offset)))
            :speed speed
            :glyphs glyphs}]))

(defn spawn-many [simulation-config state]
  (reduce (fn [[state drops] id]
            (let [[state drop] (spawn simulation-config state id)]
              [state (conj drops drop)]))
          [state []]
          (range (get-in simulation-config [:rain :drop-count]))))

(defn- mutation-probability [changes-per-second seconds]
  (- 1.0 (Math/exp (* -1.0 changes-per-second seconds))))

(defn- mutate-glyphs [simulation-config state glyphs seconds]
  (let [probability (mutation-probability
                     (get-in simulation-config
                             [:rain :glyph-changes-per-second])
                     seconds)]
    (if (zero? probability)
      [state glyphs]
      (reduce
       (fn [[state changed] glyph]
         (let [[state chance] (random/next-double state)]
           (if (< chance probability)
             (let [[state replacement] (random-glyph simulation-config state)]
               [state (conj changed replacement)])
             [state (conj changed glyph)])))
       [state []]
       glyphs))))

(defn advance
  "Move, mutate, or respawn one stream using elapsed seconds."
  [simulation-config speed-multiplier seconds state
   {:keys [id head-y speed glyphs] :as drop}]
  (let [head-y (+ head-y (* speed-multiplier speed seconds))
        length (count glyphs)]
    (if (> (- head-y length) (config/grid-height simulation-config))
      (spawn simulation-config state id)
      (let [[state glyphs] (mutate-glyphs simulation-config state glyphs
                                           seconds)]
        [state (assoc drop :head-y head-y :glyphs glyphs)]))))

(defn advance-all
  [simulation-config speed-multiplier seconds state drops]
  (reduce (fn [[state advanced] drop]
            (let [[state drop]
                  (advance simulation-config speed-multiplier seconds state drop)]
              [state (conj advanced drop)]))
          [state []]
          drops))
