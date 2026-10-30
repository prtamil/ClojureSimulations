;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.render.quil
  (:require
   [quil.core :as q]
   [quil.middleware :as m]
   [sim.utility.clock :as clock]
   [sim.model.config :as config]
   [sim.app.controller :as controller]
   [sim.model.core :as model]
   [sim.app.runtime :as runtime]
   [sim.utility.theme :as theme]))

(defn- render-setting [world setting]
  (get-in world [:config :render setting]))

(defn- canvas-size [world]
  (let [simulation-config (:config world)
        cell-size (render-setting world :cell-size)]
    [(* (config/grid-width simulation-config) cell-size)
     (+ (render-setting world :hud-height)
        (* (config/grid-height simulation-config) cell-size)
        (render-setting world :footer-height))]))

(defn- active-theme [world]
  (theme/current-theme world))

(defn- fill!
  ([[red green blue]]
   (q/fill red green blue))
  ([[red green blue] alpha]
   (q/fill red green blue alpha)))

(defn setup []
  (q/frame-rate (get-in @runtime/world [:config :clock :render-fps]))
  (q/color-mode :rgb 255)
  (q/ellipse-mode :center)
  (q/text-size 13)
  (clock/initial-state (System/nanoTime)))

(defn update-state [clock-state]
  (let [world @runtime/world
        max-delta-ns (* clock/nanoseconds-per-millisecond
                        (get-in world [:config :clock :max-frame-delta-ms]))
        {:keys [clock-state ticks dt]}
        (clock/advance clock-state
                       (System/nanoTime)
                       (get-in world [:clock :simulation-fps])
                       max-delta-ns)]
    (when (pos? ticks)
      (swap! runtime/world model/advance-world-steps ticks dt))
    clock-state))

(defn- draw-boundary! [world]
  (let [simulation-config (:config world)
        cell-size (render-setting world :cell-size)
        hud-height (render-setting world :hud-height)
        width (* (config/grid-width simulation-config) cell-size)
        height (* (config/grid-height simulation-config) cell-size)
        [red green blue] (render-setting world :boundary)]
    (q/no-fill)
    (q/stroke red green blue)
    (q/stroke-weight 1.0)
    (q/rect 0 hud-height (dec width) (dec height))))

(defn- draw-ball! [world]
  (let [cell-size (render-setting world :cell-size)
        hud-height (render-setting world :hud-height)
        {:keys [x y radius]} (:ball world)
        [deep mid bright highlight] (:bands (active-theme world))
        center-x (* x cell-size)
        center-y (+ hud-height (* y cell-size))
        diameter (* 2.0 radius cell-size)]
    (q/no-stroke)
    (fill! deep 80)
    (q/ellipse center-x center-y (* diameter 1.8) (* diameter 1.8))
    (fill! mid)
    (q/ellipse center-x center-y diameter diameter)
    (fill! bright)
    (q/ellipse (- center-x (* diameter 0.12))
               (- center-y (* diameter 0.12))
               (* diameter 0.58)
               (* diameter 0.58))
    (fill! highlight)
    (q/ellipse (- center-x (* diameter 0.22))
               (- center-y (* diameter 0.22))
               (* diameter 0.16)
               (* diameter 0.16))))

(defn- draw-palette! [world x y]
  (q/no-stroke)
  (doseq [[offset rgb] (map-indexed vector (:bands (active-theme world)))]
    (fill! rgb)
    (q/rect (+ x (* offset 17)) y 14 10)))

(defn- draw-hud! [world]
  (let [hud-height (render-setting world :hud-height)
        footer-height (render-setting world :footer-height)
        {:keys [x y vx vy]} (:ball world)]
    (q/no-stroke)
    (fill! (render-setting world :hud-background))
    (q/rect 0 0 (q/width) hud-height)
    (q/rect 0 (- (q/height) footer-height) (q/width) footer-height)
    (fill! (:flash (active-theme world)))
    (q/text-size 18)
    (q/text "BOUNCING BALL" 14 23)
    (draw-palette! world 510 12)
    (fill! (render-setting world :hud-text))
    (q/text-size 12)
    (q/text (format (str "tick %,d    time %.2fs    pos %.1f,%.1f    "
                         "vel %.1f,%.1f    speed x%.2f    %s%s")
                    (get-in world [:clock :tick])
                    (get-in world [:clock :elapsed-seconds])
                    x y vx vy
                    (:speed-multiplier world)
                    (:name (active-theme world))
                    (if (get-in world [:clock :paused?]) "  [PAUSED]" ""))
            14 45)
    (fill! (second (:bands (active-theme world))))
    (q/text "t/T theme    r reset    space pause    +/- speed    [ / ] Hz    q quit"
            14 (- (q/height) 10))))

(defn draw [_clock-state]
  (let [world @runtime/world]
    (apply q/background (render-setting world :background))
    (draw-boundary! world)
    (draw-ball! world)
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
        (q/defsketch bouncing-ball
          :title "Bouncing Ball"
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
