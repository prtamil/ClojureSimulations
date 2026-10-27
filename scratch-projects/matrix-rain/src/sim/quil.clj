;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.quil
  (:require [quil.core :as q]
            [quil.middleware :as m]
            [sim.ascii :as ascii]
            [sim.clock :as clock]
            [sim.config :as config]
            [sim.controller :as controller]
            [sim.model :as model]
            [sim.runtime :as runtime]
            [sim.theme :as theme]))

(defn- render-setting [world setting]
  (get-in world [:config :render setting]))

(defn- canvas-size [world]
  (let [cell-size (render-setting world :cell-size)]
    [(* (config/grid-width (:config world)) cell-size)
     (+ (render-setting world :hud-height)
        (* (config/grid-height (:config world)) cell-size)
        (render-setting world :footer-height))]))

(defn- active-theme [world]
  (theme/current-theme world))

(defn- fill!
  ([[red green blue]] (q/fill red green blue))
  ([[red green blue] alpha] (q/fill red green blue alpha)))

(defn setup []
  (let [world @runtime/world
        cell-size (render-setting world :cell-size)]
    (q/frame-rate (get-in world [:config :clock :render-fps]))
    (q/color-mode :rgb 255)
    (q/text-align :center :center)
    (q/text-size cell-size)
    (clock/initial-state (System/nanoTime))))

(defn update-state [clock-state]
  (let [world @runtime/world
        max-delta-ns (* clock/nanoseconds-per-millisecond
                        (get-in world [:config :clock :max-frame-delta-ms]))
        {:keys [clock-state ticks dt]}
        (clock/advance clock-state (System/nanoTime) (:sim-fps world) max-delta-ns)]
    (when (pos? ticks)
      (swap! runtime/world model/update-world-steps ticks dt))
    clock-state))

(defn- trail-color [world depth length]
  (let [bands (:bands (active-theme world))
        progress (/ depth (double (max 1 (dec length))))
        index (-> (* (- 1.0 progress) (dec (count bands)))
                  Math/floor
                  int
                  (max 0)
                  (min (dec (count bands))))]
    (nth bands index)))

(defn- draw-rain! [world]
  (let [cell-size (render-setting world :cell-size)
        hud-height (render-setting world :hud-height)]
    (q/text-size cell-size)
    (doseq [{:keys [x y depth length glyph]} (ascii/visible-cells world)]
      (if (zero? depth)
        (fill! (:flash (active-theme world)))
        (fill! (trail-color world depth length)
               (max 55 (- 235 (* depth 12)))))
      (q/text (str glyph)
              (+ (* x cell-size) (/ cell-size 2.0))
              (+ hud-height (* y cell-size) (/ cell-size 2.0))))))

(defn- draw-palette! [world x y]
  (q/no-stroke)
  (doseq [[offset rgb] (map-indexed vector (:bands (active-theme world)))]
    (fill! rgb)
    (q/rect (+ x (* offset 17)) y 14 10)))

(defn- draw-hud! [world]
  (let [hud-height (render-setting world :hud-height)
        footer-height (render-setting world :footer-height)]
    (q/no-stroke)
    (fill! (render-setting world :hud-background))
    (q/rect 0 0 (q/width) hud-height)
    (q/rect 0 (- (q/height) footer-height) (q/width) footer-height)
    (q/text-align :left :baseline)
    (fill! (:flash (active-theme world)))
    (q/text-size 18)
    (q/text "MATRIX RAIN" 14 23)
    (draw-palette! world 510 12)
    (fill! (render-setting world :hud-text))
    (q/text-size 12)
    (q/text (format (str "tick %,d    time %.2fs    streams %d    "
                         "speed x%.2f    %d Hz    %s%s")
                    (:tick world)
                    (:elapsed-seconds world)
                    (count (:drops world))
                    (:speed-mult world)
                    (:sim-fps world)
                    (:name (active-theme world))
                    (if (:paused? world) "  [PAUSED]" ""))
            14 45)
    (fill! (second (:bands (active-theme world))))
    (q/text "t/T theme    r reset    space pause    +/- speed    [ / ] Hz    q quit"
            14 (- (q/height) 10))
    (q/text-align :center :center)))

(defn draw [_clock-state]
  (let [world @runtime/world]
    (apply q/background (render-setting world :background))
    (draw-rain! world)
    (draw-hud! world)))

(defn key-pressed [clock-state event]
  (let [command (controller/key-event->command event)]
    (if (= :quit command)
      (q/exit)
      (when command (swap! runtime/world controller/apply-command command)))
    clock-state))

(defn on-close [clock-state]
  (runtime/release-gui!)
  clock-state)

(defn run-gui []
  (when (runtime/claim-gui!)
    (try
      (let [[width height] (canvas-size @runtime/world)]
        #_{:clj-kondo/ignore [:unresolved-symbol]}
        (q/defsketch matrix-rain
          :title "Matrix Rain"
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
