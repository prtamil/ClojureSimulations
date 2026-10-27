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

(defn- render-setting [world setting]
  (get-in world [:config :render setting]))

(defn- canvas-size [world]
  (let [simulation-config (:config world)
        cell-size (render-setting world :cell-size)]
    [(* (config/grid-width simulation-config) cell-size)
     (+ (render-setting world :hud-height)
        (* (config/grid-height simulation-config) cell-size)
        (render-setting world :footer-height))]))

(defn- fill! [[red green blue]]
  (q/fill red green blue))

(defn- active-theme [world]
  (theme/current-theme world))

(defn setup []
  (q/frame-rate (get-in @runtime/world [:config :clock :render-fps]))
  (q/color-mode :rgb 255)
  (q/no-stroke)
  (q/text-size 13)
  (clock/initial-state (System/nanoTime)))

(defn update-state [clock-state]
  (let [world @runtime/world
        max-delta-ns (* clock/nanoseconds-per-millisecond
                        (get-in world [:config :clock :max-frame-delta-ms]))
        {:keys [clock-state ticks dt]}
        (clock/advance clock-state
                       (System/nanoTime)
                       (:sim-fps world)
                       max-delta-ns)]
    (when (pos? ticks)
      (swap! runtime/world model/update-world-steps ticks dt))
    clock-state))

(defn- draw-particles! [world]
  (let [cell-size (render-setting world :cell-size)
        hud-height (render-setting world :hud-height)
        bands (:bands (active-theme world))]
    (doseq [[index {:keys [x y]}] (map-indexed vector (:particles world))]
      (fill! (nth bands (mod (inc index) (count bands))))
      (q/rect (* x cell-size)
              (+ hud-height (* y cell-size))
              cell-size
              cell-size))))

(defn- draw-palette! [world x y]
  (doseq [[offset rgb] (map-indexed vector (:bands (active-theme world)))]
    (fill! rgb)
    (q/rect (+ x (* offset 17)) y 14 10)))

(defn- draw-hud! [world]
  (let [hud-height (render-setting world :hud-height)
        footer-height (render-setting world :footer-height)]
    (fill! (render-setting world :hud-background))
    (q/rect 0 0 (q/width) hud-height)
    (q/rect 0 (- (q/height) footer-height) (q/width) footer-height)
    (fill! (:flash (active-theme world)))
    (q/text-size 17)
    (q/text "BASIC CLOJURE SIMULATION FRAMEWORK" 14 22)
    (draw-palette! world 510 12)
    (fill! (render-setting world :hud-text))
    (q/text-size 12)
    (q/text (format "tick %,d    time %.2fs    %d Hz    theme %s%s"
                    (:tick world)
                    (:elapsed-seconds world)
                    (:sim-fps world)
                    (:name (active-theme world))
                    (if (:paused? world) "    [PAUSED]" ""))
            14 43)
    (fill! (second (:bands (active-theme world))))
    (q/text "t/T theme    r reset    space pause    [ / ] rate    q quit"
            14 (- (q/height) 10))))

(defn draw [_clock-state]
  (let [world @runtime/world]
    (apply q/background (render-setting world :background))
    (draw-particles! world)
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
        (q/defsketch basic-clojure-simulation-framework
          :title "Basic Clojure Simulation Framework"
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
