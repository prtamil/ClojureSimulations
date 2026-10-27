;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
;;
;; MODEL LEVEL GUIDE
;; -----------------
;; Stream positions and glyphs are model data. Renderers never call rand, so a
;; world can be rendered repeatedly, tested, serialized, and replayed exactly.
(ns sim.model
  (:require [sim.config :as config]
            [sim.random :as random]))

(def default-seed 2026)

(defn- random-between [state low high]
  (let [[state' sample] (random/next-double state)]
    [state' (+ (double low) (* sample (- (double high) (double low))))]))

(defn- random-glyph [simulation-config state]
  (let [charset (vec (get-in simulation-config [:rain :charset]))
        [state' index] (random/next-int state (count charset))]
    [state' (nth charset index)]))

(defn- random-glyphs [simulation-config state length]
  (loop [remaining length state state glyphs []]
    (if (zero? remaining)
      [state glyphs]
      (let [[state' glyph] (random-glyph simulation-config state)]
        (recur (dec remaining) state' (conj glyphs glyph))))))

(defn spawn-drop
  "Create one deterministic stream and return [next-rng-state drop]."
  [simulation-config state id]
  (let [width (config/grid-width simulation-config)
        length-min (get-in simulation-config [:rain :length :min])
        length-max (get-in simulation-config [:rain :length :max])
        speed-min (get-in simulation-config [:rain :speed :min])
        speed-max (get-in simulation-config [:rain :speed :max])
        spawn-depth (get-in simulation-config [:rain :spawn-depth])
        [state column] (random/next-int state width)
        [state row-offset] (random/next-int state spawn-depth)
        [state length-offset] (random/next-int state (inc (- length-max length-min)))
        length (+ length-min length-offset)
        [state speed] (random-between state speed-min speed-max)
        [state glyphs] (random-glyphs simulation-config state length)]
    [state {:id id
            :column column
            :head-y (double (- (inc row-offset)))
            :speed speed
            :length length
            :glyphs glyphs}]))

(defn- spawn-drops [simulation-config state]
  (loop [id 0 state state drops []]
    (if (= id (get-in simulation-config [:rain :drop-count]))
      [state drops]
      (let [[state' drop] (spawn-drop simulation-config state id)]
        (recur (inc id) state' (conj drops drop))))))

(defn- mutate-glyphs [simulation-config state glyphs]
  (let [probability (get-in simulation-config [:rain :glyph-change-probability])]
    (if (zero? probability)
      [state glyphs]
      (loop [remaining glyphs state state changed []]
        (if (empty? remaining)
          [state changed]
          (let [[state' chance] (random/next-double state)]
            (if (< chance probability)
              (let [[state'' glyph] (random-glyph simulation-config state')]
                (recur (rest remaining) state'' (conj changed glyph)))
              (recur (rest remaining) state' (conj changed (first remaining))))))))))

(defn update-drop
  "Advance or respawn one stream, returning [next-rng-state drop]."
  [simulation-config speed-mult dt state
   {:keys [id head-y speed length glyphs] :as drop}]
  (let [head-y' (+ head-y (* speed-mult speed dt))
        height (config/grid-height simulation-config)]
    (if (> (- head-y' length) height)
      (spawn-drop simulation-config state id)
      (let [[state' glyphs'] (mutate-glyphs simulation-config state glyphs)]
        [state' (assoc drop :head-y head-y' :glyphs glyphs')]))))

(defn- update-drops [world state drops dt]
  (loop [remaining drops state state updated []]
    (if (empty? remaining)
      [state updated]
      (let [[state' drop']
            (update-drop (:config world) (:speed-mult world) dt
                         state (first remaining))]
        (recur (rest remaining) state' (conj updated drop'))))))

(defn initial-world
  ([] (initial-world config/default-config default-seed))
  ([seed-or-config]
   (if (map? seed-or-config)
     (initial-world seed-or-config default-seed)
     (initial-world config/default-config seed-or-config)))
  ([simulation-config seed]
   (let [simulation-config (config/validate! simulation-config)
         [rng-state permutation] (random/shuffled-permutation seed)
         [rng-state drops] (spawn-drops simulation-config rng-state)]
     {:config simulation-config
      :seed seed
      :rng-state rng-state
      :noise-permutation permutation
      :tick 0
      :elapsed-seconds 0.0
      :paused? false
      :speed-mult (get-in simulation-config [:rain :speed-multiplier :initial])
      :theme-index 0
      :sim-fps (get-in simulation-config [:clock :simulation-fps :initial])
      :drops drops})))

(defn update-world-steps [world tick-count dt]
  (when-not (nat-int? tick-count)
    (throw (ex-info "Tick count must be a non-negative integer"
                    {:tick-count tick-count})))
  (when-not (and (number? dt) (Double/isFinite (double dt)) (pos? dt))
    (throw (ex-info "Fixed-step duration must be a positive finite number"
                    {:dt dt})))
  (if (or (zero? tick-count) (:paused? world))
    world
    (loop [remaining tick-count
           state (:rng-state world)
           drops (:drops world)]
      (if (zero? remaining)
        (assoc world
               :tick (+ (:tick world) tick-count)
               :elapsed-seconds (+ (:elapsed-seconds world)
                                   (* tick-count (double dt)))
               :rng-state state
               :drops drops)
        (let [[state' drops'] (update-drops world state drops dt)]
          (recur (dec remaining) state' drops'))))))

(defn update-world
  ([world] (update-world world (/ 1.0 (:sim-fps world))))
  ([world dt] (update-world-steps world 1 dt)))

(defn toggle-paused [world]
  (update world :paused? not))

(defn adjust-speed [world direction]
  (let [{:keys [min max]} (get-in world [:config :rain :speed-multiplier])]
    (update world :speed-mult
            #(if (pos? direction)
               (clojure.core/min max (* 2.0 %))
               (clojure.core/max min (/ % 2.0))))))

(defn adjust-sim-fps [world delta]
  (let [{:keys [min max]} (get-in world [:config :clock :simulation-fps])]
    (update world :sim-fps
            #(clojure.core/min max (clojure.core/max min (+ % delta))))))

(defn reset-world [world]
  (let [fresh (initial-world (:config world) (:seed world))]
    (assoc fresh
           :paused? (:paused? world)
           :speed-mult (:speed-mult world)
           :theme-index (:theme-index world)
           :sim-fps (:sim-fps world))))

(comment
  ;; MODEL GUIDE FOR A REPL PROMPT
  (require '[sim.model :as model] :reload)

  (def world-0 (model/initial-world 42))
  (select-keys world-0
               [:seed :tick :elapsed-seconds :paused? :speed-mult :sim-fps])
  (take 3 (:drops world-0))

  (def world-60
    (model/update-world-steps world-0 60 (/ 1.0 60.0)))
  (select-keys world-60 [:tick :elapsed-seconds :rng-state])
  (= world-0 (model/initial-world 42)) ; deterministic

  ;; A rendered world is stable because glyphs live here, not in the renderer.
  (= (:drops world-60) (:drops (model/update-world-steps world-0 60
                                                        (/ 1.0 60.0))))

  ;; Force a respawn boundary and inspect its deterministic replacement.
  (def expired-world
    (assoc-in world-0 [:drops 0 :head-y] 100.0))
  (first (:drops (model/update-world expired-world)))

  (require '[clojure.test :as test]
           '[sim.model-test]
           :reload)
  (test/run-tests 'sim.model-test)
  )
