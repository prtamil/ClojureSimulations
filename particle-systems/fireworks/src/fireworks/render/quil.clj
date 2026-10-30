;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.render.quil
  (:require
   [quil.core :as q]
   [quil.middleware :as m]
   [fireworks.app.controller :as controller]
   [fireworks.app.runtime :as runtime]
   [fireworks.model.config :as config]
   [fireworks.model.core :as model]
   [fireworks.render.glyphs :as glyphs]
   [fireworks.utility.clock :as clock]
   [fireworks.utility.theme :as theme]))

(defn- render-setting [world setting]
  (get-in world [:config :render setting]))

(defn- canvas-size [world]
  (let [simulation-config (:config world)]
    [(* (config/grid-width simulation-config)
        (render-setting world :cell-width))
     (* (config/grid-height simulation-config)
        (render-setting world :cell-height))]))

(defn- fill! [[red green blue]]
  (q/fill red green blue))

(defn- fill-alpha! [[red green blue] alpha]
  (q/fill red green blue alpha))

(defn setup []
  (let [world @runtime/world]
    (q/frame-rate (get-in world [:config :clock :render-fps]))
    (q/color-mode :rgb 255)
    (q/no-stroke)
    (q/text-font (q/create-font "Monospaced"
                                (render-setting world :font-size) true))
    (q/text-align :left :top)
    (clock/initial-state (System/nanoTime))))

(defn update-state [clock-state]
  (let [world @runtime/world
        max-delta-ns (* clock/nanoseconds-per-millisecond
                        (get-in world [:config :clock :max-frame-delta-ms]))
        {:keys [clock-state ticks dt]}
    (clock/advance clock-state (System/nanoTime)
                   (get-in world [:clock :simulation-fps])
                       max-delta-ns)]
    (when (pos? ticks)
      (swap! runtime/world model/advance-world-steps ticks dt))
    clock-state))

(defn- draw-glyph! [world {:keys [column row glyph life bold? color-index]}]
  (let [cell-width (render-setting world :cell-width)
        cell-height (render-setting world :cell-height)
        color (theme/spark-rgb world color-index)
        alpha (int (max 45 (min 255 (* 255.0 (double life)))))
        x (* column cell-width)
        y (* row cell-height)]
    (fill-alpha! color alpha)
    (q/text (str glyph) x y)
    (when bold?
      (q/text (str glyph) (inc x) y))))

(defn- draw-show! [world]
  (q/text-size (render-setting world :font-size))
  (doseq [glyph (glyphs/project-show world)]
    (draw-glyph! world glyph)))

(defn- draw-palette! [world x y]
  (doseq [[offset palette-index]
          (map-indexed vector (:spark-colors (theme/current-theme world)))]
    (fill! (theme/xterm-rgb palette-index))
    (q/rect (+ x (* offset 17)) y 13 9)))

(defn- draw-hud! [world]
  (let [hud-height (render-setting world :hud-height)
        footer-height (render-setting world :footer-height)
        active-theme (theme/current-theme world)]
    (q/fill 0 0 0 205)
    (q/rect 0 0 (q/width) hud-height)
    (q/rect 0 (- (q/height) footer-height) (q/width) footer-height)
    (fill! (render-setting world :hud-text))
    (q/text-size 15)
    (q/text (format "FIREWORKS   %s   %d Hz   %d rockets   tick %,d%s"
                    (:name active-theme)
                    (get-in world [:clock :simulation-fps])
                    (model/firework-count world)
                    (get-in world [:clock :tick])
                    (if (get-in world [:clock :paused?]) "   [PAUSED]" ""))
            12 8)
    (draw-palette! world (- (q/width) 132) 12)
    (fill! (render-setting world :hint-text))
    (q/text-size 12)
    (q/text (str "q quit   ]/[ Hz   +/- rockets   t/T theme   "
                 "space pause   r reset")
            12 (- (q/height) 21))))

(defn draw [_clock-state]
  (let [world @runtime/world]
    (apply q/background (render-setting world :background))
    (draw-show! world)
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
        (q/defsketch fireworks
          :title "ASCII Fireworks"
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

(defn -main [& _args]
  (run-gui))
