;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.render.quil
  (:require
   [quil.core :as q]
   [quil.middleware :as m]
   [bonsai.app.controller :as controller]
   [bonsai.app.runtime :as runtime]
   [bonsai.model.config :as config]
   [bonsai.model.core :as model]
   [bonsai.render.cells :as cells]
   [bonsai.utility.clock :as clock]
   [bonsai.utility.theme :as theme]))

(defn- render-setting [world setting]
  (get-in world [:config :render setting]))

(defn- canvas-size [world]
  (let [simulation-config (:config world)]
    [(* (config/grid-width simulation-config)
        (render-setting world :cell-width))
     (+ (render-setting world :hud-height)
        (* (config/grid-height simulation-config)
           (render-setting world :cell-height))
        (render-setting world :footer-height))]))

(defn- fill! [[red green blue]]
  (q/fill red green blue))

(defn setup []
  (let [world @runtime/world
        font-size (render-setting world :font-size)]
    (q/frame-rate (get-in world [:config :clock :render-fps]))
    (q/color-mode :rgb 255)
    (q/no-stroke)
    (q/text-font (q/create-font "Monospaced" font-size true))
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

(defn- draw-cell! [world active-theme cell]
  (when-let [glyph (cells/display-glyph world cell)]
    (let [cell-width (render-setting world :cell-width)
          cell-height (render-setting world :cell-height)
          hud-height (render-setting world :hud-height)]
      (fill! (theme/cell-rgb active-theme cell))
      (q/text (str glyph)
              (* (:column cell) cell-width)
              (+ hud-height (* (:row cell) cell-height))))))

(defn- draw-tree! [world]
  (let [active-theme (theme/current-theme world)]
    ;; draw-hud! finishes each frame at 12px. Restore the tree glyph size on
    ;; every frame so leaves and bark do not silently shrink after frame one.
    (q/text-size (render-setting world :font-size))
    (doseq [cell (vals (:cells world))]
      (draw-cell! world active-theme cell))))

(defn- draw-palette! [active-theme x y]
  (doseq [[offset palette-index]
          (map-indexed vector (concat (:trunk active-theme)
                                      (:foliage active-theme)))]
    (fill! (theme/xterm-rgb palette-index))
    (q/rect (+ x (* offset 15)) y 12 9)))

(defn- draw-hud! [world]
  (let [active-theme (theme/current-theme world)
        style (model/current-style world)
        hud-height (render-setting world :hud-height)
        footer-height (render-setting world :footer-height)]
    (fill! (render-setting world :hud-background))
    (q/rect 0 0 (q/width) hud-height)
    (q/rect 0 (- (q/height) footer-height) (q/width) footer-height)
    (fill! (:accent active-theme))
    (q/text-size 19)
    (q/text "BONSAI — GENERATIVE ASCII GARDEN" 15 10)
    (q/text-size 12)
    (fill! (render-setting world :hud-text))
    (q/text (format (str "%s · %s    theme %s    seed %d    %,d segments / "
                         "%,d canopy sites")
                    (:name style) (:description style) (:name active-theme)
                    (:seed world)
                    (+ (count (get-in world [:tree :trunk :segments]))
                       (count (get-in world [:tree :crown :branches])))
                    (count (get-in world [:tree :crown :foliage])))
            15 37)
    (q/text (format "%d Hz    wind %d    tick %,d%s%s"
                    (get-in world [:clock :simulation-fps])
                    (:wind world)
                    (get-in world [:clock :tick])
                    (if (get-in world [:clock :paused?]) "    [PAUSED]" "")
                    (if (< (:growth-progress world) 1.0)
                      (format "    growing %d%%"
                              (int (* 100 (:growth-progress world))))
                      ""))
            15 55)
    (draw-palette! active-theme (- (q/width) 150) 18)
    (fill! (:accent active-theme))
    (q/text-size 12)
    (q/text (str "n/N style   t/T theme   r new tree   g regrow   "
                 "+/- wind   [/] Hz   space pause   q quit")
            15 (- (q/height) 23))))

(defn draw [_clock-state]
  (let [world @runtime/world
        active-theme (theme/current-theme world)]
    (apply q/background (:background active-theme))
    (draw-tree! world)
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
        (q/defsketch bonsai
          :title "Bonsai — Generative ASCII Garden"
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
