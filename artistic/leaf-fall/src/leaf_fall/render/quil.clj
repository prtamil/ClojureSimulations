;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.render.quil
  (:require
   [clojure.string :as str]
   [quil.core :as q]
   [quil.middleware :as m]
   [leaf-fall.app.controller :as controller]
   [leaf-fall.app.runtime :as runtime]
   [leaf-fall.model.config :as config]
   [leaf-fall.model.core :as model]
   [leaf-fall.render.batch :as batch]
   [leaf-fall.render.cells :as cells]
   [leaf-fall.utility.clock :as clock]
   [leaf-fall.utility.theme :as theme]))

(defn- render-setting [world key]
  (get-in world [:config :render key]))

(defn- canvas-size [world]
  (let [simulation-config (:config world)]
    [(* (config/grid-width simulation-config)
        (render-setting world :cell-width))
     (+ (render-setting world :hud-height)
        (* (config/grid-height simulation-config)
           (render-setting world :cell-height))
        (render-setting world :footer-height))]))

(defn- scene-size [world]
  (let [simulation-config (:config world)]
    [(* (config/grid-width simulation-config)
        (render-setting world :cell-width))
     (* (config/grid-height simulation-config)
        (render-setting world :cell-height))]))

(defn- fill! [[red green blue]]
  (q/fill red green blue))

(defn setup []
  (let [world @runtime/world
        font (q/create-font "Monospaced"
                            (render-setting world :font-size)
                            true)
        [scene-width scene-height] (scene-size world)]
    (q/frame-rate (get-in world [:config :clock :render-fps]))
    (q/color-mode :rgb 255)
    (q/no-stroke)
    (q/text-font font)
    (q/text-align :left :top)
    {:clock-state (clock/initial-state (System/nanoTime))
     :scene-layer (q/create-graphics scene-width scene-height :p2d)
     :scene-font font
     :scene-cache-key (volatile! nil)}))

(defn update-state [renderer-state]
  (let [world @runtime/world
        max-delta-ns (* clock/nanoseconds-per-millisecond
                        (get-in world [:config :clock :max-frame-delta-ms]))
        {:keys [clock-state ticks dt]}
        (clock/advance (:clock-state renderer-state)
                       (System/nanoTime)
                       (get-in world [:clock :simulation-fps])
                       max-delta-ns)]
    (when (pos? ticks)
      (swap! runtime/world model/advance-world-steps ticks dt))
    (assoc renderer-state :clock-state clock-state)))

(defn- scene-cache-key [world]
  [(:seed world)
   (:cycle world)
   (:phase world)
   (when (= :falling (:phase world)) (get-in world [:clock :tick]))])

(defn- draw-cell-rows! [world]
  (let [cell-width (render-setting world :cell-width)
        cell-height (render-setting world :cell-height)
        simulation-config (:config world)
        rows (batch/cell-rows (config/grid-height simulation-config)
                              (config/grid-width simulation-config)
                              (cells/visible-cells world))
        glyph-width (max 1.0 (q/text-width "M"))
        horizontal-scale (/ cell-width glyph-width)]
    (q/push-matrix)
    (q/scale horizontal-scale 1.0)
    (doseq [{:keys [role row text]} rows]
      (fill! (theme/cell-rgb {:role role}))
      (q/text text 0 (* row cell-height)))
    (q/pop-matrix)))

(defn- refresh-scene-layer! [renderer-state world cache-key]
  (q/with-graphics (:scene-layer renderer-state)
    (apply q/background (render-setting world :background))
    (q/no-stroke)
    (q/text-font (:scene-font renderer-state))
    (q/text-align :left :top)
    (q/text-size (render-setting world :font-size))
    (draw-cell-rows! world))
  (vreset! (:scene-cache-key renderer-state) cache-key))

(defn- draw-scene! [renderer-state world]
  (let [cache-key (scene-cache-key world)]
    (when (not= cache-key @(:scene-cache-key renderer-state))
      (refresh-scene-layer! renderer-state world cache-key))
    (q/image (:scene-layer renderer-state)
             0
             (render-setting world :hud-height))))

(defn- draw-hud! [world]
  (let [hud-height (render-setting world :hud-height)
        footer-height (render-setting world :footer-height)
        total-leaves (count (:leaf-templates world))]
    (fill! (render-setting world :panel-background))
    (q/rect 0 0 (q/width) hud-height)
    (q/rect 0 (- (q/height) footer-height) (q/width) footer-height)
    (fill! (render-setting world :hud-accent))
    (q/text-size 19)
    (q/text "LEAF FALL — PROCEDURAL ASCII TREE" 15 10)
    (fill! (render-setting world :hud-text))
    (q/text-size 12)
    (q/text (format "%s    cycle %,d    seed %d    leaves %,d / %,d"
                    (str/upper-case (model/phase-name world))
                    (:cycle world)
                    (:seed world)
                    (model/active-leaf-count world)
                    total-leaves)
            15 38)
    (q/text (format "%d Hz    tick %,d%s"
                    (get-in world [:clock :simulation-fps])
                    (get-in world [:clock :tick])
                    (if (get-in world [:clock :paused?]) "    [PAUSED]" ""))
            15 55)
    (fill! (render-setting world :hint))
    (q/text-size 12)
    (q/text "q quit    r new tree    space skip phase    [ / ] simulation Hz"
            15 (- (q/height) 23))))

(defn draw [renderer-state]
  (let [world @runtime/world]
    (apply q/background (render-setting world :background))
    (draw-scene! renderer-state world)
    (draw-hud! world)))

(defn key-pressed [clock-state event]
  (let [command (controller/key-event->command event)]
    (if (= :quit command)
      (do
        ;; Release immediately so the standalone launcher can terminate even
        ;; before Processing finishes dispatching its on-close callback.
        (runtime/release-gui!)
        (q/exit))
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
        (q/defsketch leaf-fall
          :title "Leaf Fall — Procedural ASCII Tree"
          :size [width height]
          :renderer :p2d
          :setup setup
          :update update-state
          :draw draw
          :key-pressed key-pressed
          :on-close on-close
          :middleware [m/fun-mode]))
      (catch Throwable error
        (runtime/release-gui!)
        (throw error)))))
