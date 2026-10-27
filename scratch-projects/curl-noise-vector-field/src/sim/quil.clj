;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.quil
  (:require
   [quil.core :as q]
   [quil.middleware :as m]
   [sim.clock :as clock]
   [sim.config :as config]
   [sim.controller :as controller]
   [sim.model :as model]
   [sim.runtime :as runtime]
   [sim.theme :as theme]))

(def cell-size 8)
(def hud-height 76)
(def footer-height 32)

(defn- canvas-size [world]
  (let [simulation-config (:config world)]
    [(* (config/grid-width simulation-config) cell-size)
     (+ hud-height
        (* (config/grid-height simulation-config) cell-size)
        footer-height)]))

(defn- active-theme [world]
  (theme/current-theme world))

(defn setup []
  (q/frame-rate (get-in @runtime/world [:config :clock :render-fps]))
  (q/color-mode :rgb 255)
  (q/smooth 4)
  (q/text-size 13)
  (clock/initial-state (System/nanoTime)))

(defn update-state [clock-state]
  (let [world @runtime/world
        simulation-fps (:sim-fps world)
        max-delta-ns (* clock/nanoseconds-per-millisecond
                        (get-in world [:config :clock :max-frame-delta-ms]))
        {:keys [clock-state ticks dt]}
        (clock/advance clock-state
                       (System/nanoTime)
                       simulation-fps
                       max-delta-ns)]
    (when (pos? ticks)
      (swap! runtime/world model/update-world-steps ticks dt))
    clock-state))

(defn- set-fill! [[r g b] alpha]
  (q/fill r g b alpha))

(defn- set-stroke! [[r g b] alpha]
  (q/stroke r g b alpha))

(defn- draw-density-field! [world]
  (let [simulation-config (:config world)
        cell-count (config/cell-count simulation-config)
        width (config/grid-width simulation-config)
        glow-threshold (get-in simulation-config [:render :glow-threshold])
        {:keys [glow color]} (:buffers world)
        bands (:bands (active-theme world))]
    (q/no-stroke)
    (doseq [index (range cell-count)
            :let [brightness (nth glow index)]
            :when (> brightness glow-threshold)]
      (let [x (mod index width)
            y (quot index width)
            rgb (nth bands (nth color index))
            alpha (+ 20 (* 235 brightness))]
        (set-fill! rgb alpha)
        (q/rect (* x cell-size) (* y cell-size) cell-size cell-size)))))

(defn- draw-arrow! [x y vx vy brightness rgb velocity-epsilon]
  (let [magnitude (Math/hypot vx vy)
        ux (if (> magnitude velocity-epsilon) (/ vx magnitude) 0.0)
        uy (if (> magnitude velocity-epsilon) (/ vy magnitude) 0.0)
        length (* cell-size 1.55)
        cx (+ (* x cell-size) (/ cell-size 2.0))
        cy (+ (* y cell-size) (/ cell-size 2.0))
        ex (+ cx (* ux length))
        ey (+ cy (* uy length))
        angle (Math/atan2 uy ux)
        head 4.0]
    (set-stroke! rgb (+ 35 (* 220 brightness)))
    (q/stroke-weight 1.4)
    (q/line cx cy ex ey)
    (q/line ex ey
            (- ex (* head (Math/cos (- angle 0.55))))
            (- ey (* head (Math/sin (- angle 0.55)))))
    (q/line ex ey
            (- ex (* head (Math/cos (+ angle 0.55))))
            (- ey (* head (Math/sin (+ angle 0.55)))))))

(defn- draw-vector-field! [world]
  (let [simulation-config (:config world)
        width (config/grid-width simulation-config)
        velocity-epsilon (get-in simulation-config [:curl :velocity-epsilon])
        {:keys [glow color vectors]} (:buffers world)
        bands (:bands (active-theme world))]
    (doseq [[index [vx vy]] vectors]
      (let [x (mod index width)
            y (quot index width)
            brightness (nth glow index)]
        (draw-arrow! x y vx vy brightness
                     (nth bands (nth color index))
                     velocity-epsilon)))))

(defn- draw-particles! [world]
  (when (config/particle-pattern? (:pattern world))
    (let [bands (:bands (active-theme world))]
      (q/no-stroke)
      (doseq [{:keys [x y color]} (:particles world)]
        (set-fill! (nth bands color) 245)
        (q/ellipse (+ (* x cell-size) (/ cell-size 2.0))
                   (+ (* y cell-size) (/ cell-size 2.0))
                   3.2 3.2)))))

(defn- draw-supernova! [world]
  (let [simulation-config (:config world)
        width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)
        glow-threshold (get-in simulation-config [:render :glow-threshold])
        sparkle-mask (get-in simulation-config [:flash :sparkle-mask])
        brightness (:supernova world)]
    (when (> brightness glow-threshold)
      (q/no-stroke)
      (set-fill! (:flash (active-theme world)) (* 210 brightness))
      (doseq [y (range height)
              x (range width)
              :when (zero? (bit-and (bit-xor x y) sparkle-mask))]
        (q/rect (+ (* x cell-size) 2)
                (+ (* y cell-size) 2)
                3 3)))))

(defn- draw-field! [world]
  (q/push-matrix)
  (q/translate 0 hud-height)
  (if (= :vector (:pattern world))
    (draw-vector-field! world)
    (draw-density-field! world))
  (draw-particles! world)
  (draw-supernova! world)
  (q/pop-matrix))

(defn- draw-palette! [world x y]
  (doseq [[offset rgb] (map-indexed vector (:bands (active-theme world)))]
    (set-fill! rgb 255)
    (q/rect (+ x (* offset 17)) y 14 10)))

(defn- draw-hud! [world]
  (q/no-stroke)
  (q/fill 10 13 22 248)
  (q/rect 0 0 (q/width) hud-height)
  (q/rect 0 (- (q/height) footer-height) (q/width) footer-height)
  (q/fill 255 225 70)
  (q/text-size 18)
  (q/text "CURL NOISE VECTOR FIELD" 16 25)
  (q/text-size 13)
  (q/fill 220)
  (q/text (format "%s%s    tick %,d    %d Hz    speed %d    drift x%d"
                  (config/pattern-name (:pattern world))
                  (if (:paused? world) "  [PAUSED]" "")
                  (:tick world)
                  (:sim-fps world)
                  (:speed world)
                  (:drift-mult world))
          16 49)
  (q/text (str "theme " (:name (active-theme world))) 16 68)
  (draw-palette! world 132 58)
  (q/fill 75 220 255)
  (q/text "n/p view    t/T theme    r reset    space pause    +/- speed    [/ ] Hz    q quit"
          16 (- (q/height) 11)))

(defn draw [_clock-state]
  (let [world @runtime/world]
    (q/background 4 6 12)
    (draw-field! world)
    (draw-hud! world)))

(defn key-pressed [clock-state event]
  (let [command (controller/key-event->command event)]
    (if (= :quit command)
      (q/exit)
      (when command
        (swap! runtime/world controller/apply-command command)))
    clock-state))

(defn on-close [clock-state]
  (runtime/release-gui!)
  clock-state)

(defn run-gui []
  (when (runtime/claim-gui!)
    (try
      (let [[width height] (canvas-size @runtime/world)]
        #_{:clj-kondo/ignore [:unresolved-symbol]}
        (q/defsketch curl-noise-vector-field
          :title "Curl Noise Vector Field"
          :size [width height]
          :setup setup
          :update update-state
          :draw draw
          :key-pressed key-pressed
          :on-close on-close
          :middleware [m/fun-mode]))
      (catch Throwable error
        (runtime/release-gui!)
        (throw error)))))

