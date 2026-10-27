;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
;;
;; MODEL LEVEL GUIDE
;; -----------------
;; Keep simulation truth here as pure data transformations. Use the comment
;; block at the bottom from any REPL prompt to construct, step, and inspect
;; deterministic worlds before involving runtime atoms or renderers.
(ns sim.model
  (:require
   [sim.config :as config]
   [sim.noise :as noise]
   [sim.random :as random]))

;; Scene data -----------------------------------------------------------------

(defn- blank-buffers [simulation-config]
  (let [cell-count (config/cell-count simulation-config)]
    {:glow (vec (repeat cell-count 0.0))
     :color (vec (repeat cell-count 0))
     :vectors {}}))

(defn- spawn-particle [simulation-config seed]
  (let [width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)
        band-count (get-in simulation-config [:render :band-count])
        age-min (get-in simulation-config [:particles :lifetime :min-ticks])
        age-max (get-in simulation-config [:particles :lifetime :max-ticks])
        [seed x] (random/next-int seed width)
        [seed y] (random/next-int seed height)
        [seed color] (random/next-int seed band-count)
        [seed age-range] (random/next-int seed (- age-max age-min))]
    [seed {:x (double x)
           :y (double y)
           :color color
           :age 0
           :max-age (+ age-min age-range)}]))

(defn- spawn-particles [simulation-config seed]
  (loop [n (get-in simulation-config [:particles :count])
         rng seed
         particles (transient [])]
    (if (zero? n)
      [rng (persistent! particles)]
      (let [[rng' particle] (spawn-particle simulation-config rng)]
        (recur (dec n) rng' (conj! particles particle))))))

(defn reset-world
  "Reshuffles the field and particles while preserving user controls."
  [world]
  (let [simulation-config (:config world)
        [rng permutation] (random/shuffled-permutation (:rng-state world))
        [rng particles] (spawn-particles simulation-config rng)]
    (assoc world
           :tick 0
           :field-time 0.0
           :supernova (get-in simulation-config [:flash :initial])
           :noise-permutation permutation
           :particles particles
           :buffers (blank-buffers simulation-config)
           :buffer-pattern nil
           :rng-state rng)))

(defn initial-world
  ([] (initial-world config/default-config (System/nanoTime)))
  ([seed]
   (initial-world config/default-config seed))
  ([simulation-config seed]
   (let [simulation-config (config/validate! simulation-config)]
     (reset-world
      {:config simulation-config
       :tick 0
       :field-time 0.0
       :supernova (get-in simulation-config [:flash :initial])
       :paused? false
       :speed (get-in simulation-config [:particles :speed :initial])
       :drift-mult (get-in simulation-config [:field :drift-multiplier :initial])
       :theme-index 0
       :pattern :particles
       :buffer-pattern nil
       :sim-fps (get-in simulation-config [:clock :simulation-fps :initial])
       :noise-permutation []
       :particles []
       :buffers (blank-buffers simulation-config)
       :rng-state (long seed)}))))

;; Controls -------------------------------------------------------------------

(defn toggle-paused [world]
  (update world :paused? not))

(defn- cycle-index [items current direction]
  (mod (+ (.indexOf ^java.util.List items current) direction) (count items)))

(defn cycle-pattern [world direction]
  (let [patterns (get-in world [:config :views :order])]
    (assoc world :pattern
           (nth patterns (cycle-index patterns (:pattern world) direction)))))

(defn adjust-speed [world direction]
  (let [simulation-config (:config world)
        speed-min (get-in simulation-config [:particles :speed :min])
        speed-max (get-in simulation-config [:particles :speed :max])
        drift-min (get-in simulation-config [:field :drift-multiplier :min])
        drift-max (get-in simulation-config [:field :drift-multiplier :max])]
    (if (pos? direction)
      (-> world
          (update :speed #(min speed-max (* 2 %)))
          (update :drift-mult #(min drift-max (* 2 %))))
      (-> world
          (update :speed #(max speed-min (quot % 2)))
          (update :drift-mult #(max drift-min (quot % 2)))))))

(defn adjust-sim-fps [world delta]
  (let [fps-min (get-in world [:config :clock :simulation-fps :min])
        fps-max (get-in world [:config :clock :simulation-fps :max])]
    (update world :sim-fps #(-> (+ % delta)
                                (max fps-min)
                                (min fps-max)))))

;; Pattern updates ------------------------------------------------------------

(defn- clamp [value low high]
  (max low (min high value)))

(defn- band-for [simulation-config glow]
  (let [band-count (get-in simulation-config [:render :band-count])]
    (-> (* glow band-count)
        int
        (clamp 0 (dec band-count)))))

(defn- static-cell [world x y]
  (let [simulation-config (:config world)
        permutation (:noise-permutation world)
        field-time (:field-time world)
        noise-scale (get-in simulation-config [:field :noise-scale])
        fx (* x noise-scale)
        fy (* y noise-scale)]
    (case (:pattern world)
      :potential
      (let [midpoint (get-in simulation-config [:render :potential :midpoint])
            potential-range (get-in simulation-config [:render :potential :range])
            glow (clamp (+ midpoint
                           (* potential-range
                              (noise/potential-at simulation-config
                                                  permutation fx fy field-time)))
                        0.0 1.0)]
        [glow (band-for simulation-config glow) nil])

      :curl-magnitude
      (let [[vx vy] (noise/curl-at simulation-config permutation fx fy field-time false)
            gain (get-in simulation-config [:views :curl-magnitude :gain])
            glow (clamp (* gain (Math/hypot vx vy)) 0.0 1.0)]
        [glow (band-for simulation-config glow) nil])

      :vector
      (let [[lattice-x lattice-y] (get-in simulation-config [:views :vector :lattice])]
        (when (and (zero? (mod x lattice-x))
                   (zero? (mod y lattice-y)))
          (let [[vx vy] (noise/curl-at simulation-config permutation fx fy field-time false)
                gain (get-in simulation-config [:views :curl-magnitude :gain])
                glow (clamp (* gain (Math/hypot vx vy)) 0.0 1.0)]
            [glow
             (band-for simulation-config glow)
             [vx vy]])))

      nil)))

(defn- update-static-pattern [world]
  (let [simulation-config (:config world)
        width (config/grid-width simulation-config)
        cell-count (config/cell-count simulation-config)]
    (loop [index 0
           glow (transient (vec (repeat cell-count 0.0)))
           color (transient (vec (repeat cell-count 0)))
           vectors (transient {})]
      (if (= index cell-count)
        (assoc world :buffers {:glow (persistent! glow)
                               :color (persistent! color)
                               :vectors (persistent! vectors)})
        (let [x (mod index width)
              y (quot index width)]
          (if-let [[brightness band velocity] (static-cell world x y)]
            (recur (inc index)
                   (assoc! glow index brightness)
                   (assoc! color index band)
                   (if velocity (assoc! vectors index velocity) vectors))
            (recur (inc index) glow color vectors)))))))

(defn- unit-velocity [world field-time x y warped?]
  (let [simulation-config (:config world)
        noise-scale (get-in simulation-config [:field :noise-scale])
        velocity-epsilon (get-in simulation-config [:curl :velocity-epsilon])
        [vx vy] (noise/curl-at simulation-config
                               (:noise-permutation world)
                               (* x noise-scale)
                               (* y noise-scale)
                               field-time
                               warped?)
        magnitude (Math/hypot vx vy)]
    (if (> magnitude velocity-epsilon)
      [(/ vx magnitude) (/ vy magnitude)]
      [vx vy])))

(defn- expired? [simulation-config {:keys [x y age max-age]}]
  (let [width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)]
    (or (>= age max-age)
        (< x 0.0) (>= x width)
        (< y 0.0) (>= y height))))

(defn- step-particle-tick
  [world particles rng glow color field-time deposit-intensity dt warped?]
  (let [simulation-config (:config world)
        width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)]
    (loop [particle-index 0
           rng rng
           next-particles (transient [])
           glow glow
           color color]
      (if (= particle-index (count particles))
        [(persistent! next-particles) rng glow color]
        (let [{:keys [x y] :as particle} (nth particles particle-index)
              [vx vy] (unit-velocity world field-time x y warped?)
              moved (-> particle
                        (assoc :x (+ x (* vx (:speed world) dt))
                               :y (+ y (* vy (:speed world) dt)))
                        (update :age inc))
              cell-x (int (:x moved))
              cell-y (int (:y moved))
              in-bounds? (and (<= 0 cell-x) (< cell-x width)
                              (<= 0 cell-y) (< cell-y height))
              index (+ cell-x (* cell-y width))
              glow' (if in-bounds? (assoc! glow index deposit-intensity) glow)
              color' (if in-bounds? (assoc! color index (:color particle)) color)
              [rng' final-particle] (if (expired? simulation-config moved)
                                      (spawn-particle simulation-config rng)
                                      [rng moved])]
          (recur (inc particle-index)
                 rng'
                 (conj! next-particles final-particle)
                 glow'
                 color'))))))

(defn- prepare-pattern-buffer [world]
  (if (= (:pattern world) (:buffer-pattern world))
    world
    (assoc world
           :buffers (blank-buffers (:config world))
           :buffer-pattern (:pattern world))))

(defn- refresh-current-view [world]
  (if (= (:pattern world) (:buffer-pattern world))
    world
    (let [world (prepare-pattern-buffer world)]
      (if (config/static-pattern? (:pattern world))
        (update-static-pattern world)
        world))))

(defn- update-particle-pattern-steps [world tick-count dt warped?]
  (let [world (prepare-pattern-buffer world)
        simulation-config (:config world)
        trail-decay (get-in simulation-config [:trail :decay])
        hit-intensity (get-in simulation-config [:trail :hit-intensity])
        flash-decay (get-in simulation-config [:flash :decay])
        field-step (* (get-in simulation-config [:field :drift-rate])
                      (:drift-mult world)
                      dt)
        flash-factor (Math/exp (* -1.0 flash-decay dt))
        ;; Decay the old grid once. A hit from tick i is pre-decayed by the
        ;; number of ticks still remaining, producing the same final buffer as
        ;; decaying the full grid before every individual tick.
        total-trail-decay (Math/exp (* -1.0 trail-decay dt tick-count))
        old-buffers (:buffers world)
        glow (transient (mapv #(* total-trail-decay %) (:glow old-buffers)))
        color (transient (:color old-buffers))]
    (loop [tick-index 0
           field-time (:field-time world)
           supernova (:supernova world)
           particles (:particles world)
           rng (:rng-state world)
           glow glow
           color color]
      (if (= tick-index tick-count)
        (assoc world
               :tick (+ (:tick world) tick-count)
               :field-time field-time
               :supernova supernova
               :particles particles
               :rng-state rng
               :buffers {:glow (persistent! glow)
                         :color (persistent! color)
                         :vectors {}})
        (let [field-time' (+ field-time field-step)
              supernova' (* supernova flash-factor)
              remaining-decays (- tick-count tick-index 1)
              deposit-intensity (* hit-intensity
                                   (Math/exp (* -1.0 trail-decay dt
                                                remaining-decays)))
              [particles' rng' glow' color']
              (step-particle-tick world particles rng glow color
                                  field-time' deposit-intensity dt warped?)]
          (recur (inc tick-index)
                 field-time'
                 supernova'
                 particles'
                 rng'
                 glow'
                 color'))))))

(defn- advance-static-state [world tick-count elapsed-seconds]
  (let [world (prepare-pattern-buffer world)
        simulation-config (:config world)
        flash-decay (get-in simulation-config [:flash :decay])
        drift-rate (get-in simulation-config [:field :drift-rate])]
    (-> world
        (update :tick + tick-count)
        (update :supernova
                #(* % (Math/exp (* -1.0 flash-decay elapsed-seconds))))
        (update :field-time +
                (* drift-rate (:drift-mult world) elapsed-seconds)))))

(defn update-world-steps
  "Consume one fixed-step batch for any pattern. Particle paths retain every
  intermediate integration step while shared grid work is performed once."
  [world tick-count dt]
  (when-not (nat-int? tick-count)
    (throw (ex-info "Tick count must be a non-negative integer"
                    {:tick-count tick-count})))
  (when-not (and (number? dt)
                 (Double/isFinite (double dt))
                 (pos? dt))
    (throw (ex-info "Fixed-step duration must be a positive finite number"
                    {:dt dt})))
  (when-not (config/supported-pattern? (:pattern world))
    (throw (ex-info "World contains an unsupported pattern"
                    {:pattern (:pattern world)})))
  (cond
    (zero? tick-count) world

    (:paused? world) (refresh-current-view world)

    (= :particles (:pattern world))
    (update-particle-pattern-steps world tick-count dt false)

    (= :warped (:pattern world))
    (update-particle-pattern-steps world tick-count dt true)

    (config/static-pattern? (:pattern world))
    (-> world
        (advance-static-state tick-count (* tick-count dt))
        update-static-pattern)

    :else world))

(defn update-world
  "Advance the world by one fixed simulation step."
  ([world] (update-world world (/ 1.0 (:sim-fps world))))
  ([world dt]
   (update-world-steps world 1 dt)))

(comment
  ;; MODEL GUIDE FOR A REPL PROMPT
  ;; Copy/evaluate this require first. Use :reload after editing model code.
  (require '[sim.config :as config]
           '[sim.model :as model]
           '[sim.noise :as noise]
           :reload)

  ;; 1. CREATE a reproducible world. Always use a fixed seed while debugging.
  (def world-0 (model/initial-world 42))
  (select-keys world-0
               [:tick :field-time :paused? :speed :drift-mult
                :sim-fps :pattern :theme-index])
  (count (:particles world-0))
  (first (:particles world-0))

  ;; 2. STEP one tick while retaining the original immutable value.
  (def world-1 (model/update-world world-0))
  (:tick world-0) ; => 0
  (:tick world-1) ; => 1
  (= world-0 world-1) ; => false

  ;; 3. STEP a fixed batch exactly as the runtime clock does.
  (def world-60
    (model/update-world-steps world-0 60 (/ 1.0 60.0)))
  (select-keys world-60 [:tick :field-time :supernova])
  (apply max (get-in world-60 [:buffers :glow]))

  ;; 4. Turn important expectations into immediate manual assertions.
  (assert (= 60 (:tick world-60)))
  (assert (= (get-in world-0 [:config :particles :count])
             (count (:particles world-60))))
  (assert (= world-0 (model/initial-world 42)))

  ;; 5. Exercise every view with the same world and one fixed update.
  (def worlds-by-pattern
    (into {}
          (map (fn [pattern]
                 [pattern
                  (-> world-0
                      (assoc :pattern pattern)
                      model/update-world)]))
          config/pattern-order))
  (mapv (fn [[pattern world]]
          [pattern (:tick world) (apply max (get-in world [:buffers :glow]))])
        worlds-by-pattern)
  (get-in worlds-by-pattern [:vector :buffers :vectors])

  ;; 6. Test controls as ordinary world-to-world transformations.
  (:pattern (model/cycle-pattern world-0 1)) ; => :vector
  (select-keys (model/adjust-speed world-0 1) [:speed :drift-mult])
  (def paused-world (-> world-0 model/update-world model/toggle-paused))
  (= paused-world
     (model/update-world-steps paused-world 10 (/ 1.0 60.0))) ; => true

  ;; 7. Inspect the same seeded field directly when diagnosing model motion.
  (noise/potential-at (:noise-permutation world-0) 1.2 3.4 0.0)
  (noise/curl-at (:noise-permutation world-0) 1.2 3.4 0.0 false)
  (noise/curl-at (:noise-permutation world-0) 1.2 3.4 0.0 true)

  ;; 8. Run the real model tests. The :dev alias includes test/ on the
  ;; classpath, so these forms work in a development REPL.
  (require '[clojure.test :as test]
           '[sim.model-test]
           :reload)
  (test/run-tests 'sim.model-test)
  )

