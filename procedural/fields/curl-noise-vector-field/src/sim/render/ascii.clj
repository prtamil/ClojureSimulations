;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.render.ascii
  "Pure, headless rendering of a projected curl-noise scene."
  (:require
   [clojure.string :as str]
   [sim.model.config :as config]
   [sim.render.projection :as projection]
   [sim.utility.theme :as theme]))

(defn density-glyph
  "Map normalized intensity to the shared terminal density ramp."
  [simulation-config intensity]
  (let [high (get-in simulation-config [:render :density-thresholds :high])
        middle (get-in simulation-config [:render :density-thresholds :mid])
        visible (get-in simulation-config [:render :glow-threshold])]
    (cond
      (> intensity high) \#
      (> intensity middle) \*
      (> intensity visible) \.
      :else \space)))

(defn arrow-glyph
  "Map a two-dimensional velocity to a portable directional character."
  [simulation-config [vx vy]]
  (let [stationary (get-in simulation-config
                           [:views :vector :stationary-threshold])
        dominance (get-in simulation-config [:views :vector :axis-dominance])
        abs-x (Math/abs (double vx))
        abs-y (Math/abs (double vy))]
    (cond
      (and (< abs-x stationary) (< abs-y stationary)) \.
      (> abs-x (* dominance abs-y)) (if (pos? vx) \> \<)
      (> abs-y (* dominance abs-x)) (if (pos? vy) \v \^)
      (pos? (* vx vy)) \\
      :else \/)))

(defn- particle-preview-positions
  [scene simulation-config columns rows]
  (let [width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)]
    (into #{}
          (map (fn [{[x y] :position}]
                 [(min (dec columns) (int (* x (/ columns width))))
                  (min (dec rows) (int (* y (/ rows height))))]))
          (:particles scene))))

(defn- model-position [width height columns rows screen-x screen-y]
  [(min (dec width) (int (* screen-x (/ width columns))))
   (min (dec height) (int (* screen-y (/ height rows))))])

(defn render-string
  "Return a compact headless view, defaulting to 80 by 30 characters."
  ([world]
   (render-string world {}))
  ([world {:keys [columns rows] :or {columns 80 rows 30}}]
   (let [simulation-config (:config world)
         scene (projection/project-world world)
         width (config/grid-width simulation-config)
         height (config/grid-height simulation-config)
         columns (min columns width)
         rows (min rows height)
         particle-positions
         (particle-preview-positions scene simulation-config columns rows)
         header (format "tick:%d  pattern:%s  speed:%d  drift:x%d  theme:%s%s"
                        (get-in world [:clock :tick])
                        (config/pattern-name (:pattern scene))
                        (:particle-speed world)
                        (:drift-multiplier world)
                        (:name (theme/current-theme world))
                        (if (get-in world [:clock :paused?]) "  PAUSED" ""))
         lines
         (for [screen-y (range rows)]
           (apply str
                  (for [screen-x (range columns)]
                    (if (contains? particle-positions [screen-x screen-y])
                      \@
                      (let [position (model-position width height columns rows
                                                     screen-x screen-y)
                            {:keys [intensity velocity]}
                            (get (:samples scene) position {:intensity 0.0})]
                        (if velocity
                          (arrow-glyph simulation-config velocity)
                          (density-glyph simulation-config intensity)))))))]
     (str header "\n" (str/join "\n" lines)))))

(defn render [world]
  (println (render-string world)))
