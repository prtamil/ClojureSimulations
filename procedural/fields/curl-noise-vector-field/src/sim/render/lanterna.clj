;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.render.lanterna
  "Immediate-mode terminal adapter for the curl-noise simulation."
  (:require
   [sim.app.controller :as controller]
   [sim.app.runtime :as runtime]
   [sim.model.config :as config]
   [sim.model.core :as model]
   [sim.render.ascii :as ascii]
   [sim.render.projection :as projection]
   [sim.terminal.framework :as terminal]
   [sim.utility.theme :as theme]))

(def ^:private footer
  "n/p view  t/T theme  r reset  space pause  +/- speed  [/] Hz  q/Esc quit")

(defn key->command
  "Translate a normalized terminal event into a simulation command."
  [{:keys [type character]}]
  (case type
    :character (if (= character (char 3))
                 :quit
                 (controller/key-event->command {:raw-key character}))
    :escape :quit
    :eof :quit
    nil))

(def ^:private palettes
  (mapv (fn [{:keys [bands flash]}]
          {:bands (mapv terminal/rgb bands)
           :flash (terminal/rgb flash)})
        theme/themes))

(defn- palette-for [world]
  (nth palettes (:theme-index world)))

(defn fit-to-window
  "Fit the aspect-correct model grid between the terminal HUD rows."
  [world window-columns window-rows]
  (let [simulation-config (:config world)]
    (terminal/fit-viewport
     window-columns window-rows
     (config/grid-width simulation-config)
     (config/grid-height simulation-config)
     {:cell-aspect (get-in simulation-config
                           [:render :terminal-cell-aspect])
      :padding-top 1
      :padding-bottom 1
      :border 1})))

(defn- model-position
  [world field-columns field-rows screen-x screen-y]
  (let [simulation-config (:config world)
        width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)
        x (min (dec width)
               (int (* (+ screen-x 0.5) (/ width field-columns))))
        y (min (dec height)
               (int (* (+ screen-y 0.5) (/ height field-rows))))]
    (if (= :vector (get-in world [:view :pattern]))
      (let [[lattice-x lattice-y] (get-in simulation-config
                                          [:views :vector :lattice])]
        [(- x (mod x lattice-x))
         (- y (mod y lattice-y))])
      [x y])))

(defn- particle-screen-bands [world scene layout window-columns]
  (let [simulation-config (:config world)
        width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)]
    (persistent!
     (reduce
      (fn [screen-bands {[x y] :position band :band}]
        (if-let [{:keys [column row]}
                 (terminal/project-point layout x y width height)]
          (assoc! screen-bands (+ column (* row window-columns)) band)
          screen-bands))
      (transient {})
      (:particles scene)))))

(defn- appearance-at
  [world scene palette particle-screen-bands window-columns
   screen-column screen-row
   model-x model-y]
  (let [simulation-config (:config world)
        {:keys [intensity band velocity]}
        (get (:samples scene) [model-x model-y]
             {:intensity 0.0 :band 0})
        particle-band (get particle-screen-bands
                           (+ screen-column (* screen-row window-columns)))
        glow-threshold (get-in simulation-config [:render :glow-threshold])
        high-threshold (get-in simulation-config
                               [:render :density-thresholds :high])
        sparkle-mask (get-in simulation-config [:flash :sparkle-mask])
        flash? (and (> (:flash-intensity scene) glow-threshold)
                    (zero? (bit-and (bit-xor model-x model-y)
                                    sparkle-mask)))]
    (cond
      flash?
      [\* (:flash palette) true]

      (some? particle-band)
      [\@ (nth (:bands palette) particle-band) true]

      (= :vector (:pattern scene))
      (when velocity
        [(ascii/arrow-glyph simulation-config velocity)
         (nth (:bands palette) band)
         (> intensity high-threshold)])

      :else
      (let [glyph (ascii/density-glyph simulation-config intensity)]
        (when-not (= \space glyph)
          [glyph
           (nth (:bands palette) band)
           (> intensity high-threshold)])))))

(defn- draw-field!
  [{:keys [columns] :as surface} world scene layout palette]
  (let [{field-columns :columns field-rows :rows
         left :left top :top border :border} layout
        particle-bands (particle-screen-bands world scene layout columns)]
    (when (and (pos? field-columns) (pos? field-rows))
      (terminal/set-foreground! surface (second (:bands palette)))
      (terminal/draw-box! surface left top
                          (+ field-columns (* 2 border))
                          (+ field-rows (* 2 border)))
      (dotimes [screen-y field-rows]
        (dotimes [screen-x field-columns]
          (let [screen-column (+ left border screen-x)
                screen-row (+ top border screen-y)
                [model-x model-y]
                (model-position world field-columns field-rows screen-x screen-y)]
            (when-let [[glyph color bold?]
                       (appearance-at world scene palette particle-bands
                                      columns screen-column screen-row
                                      model-x model-y)]
              (terminal/set-foreground! surface color)
              (terminal/set-bold! surface bold?)
              (terminal/put-character! surface screen-column screen-row
                                       glyph))))))))

(defn- status-text [world measured-fps]
  (format "%5.1f fps  %s%s  %,d  %dHz  speed:%d drift:x%d  %s"
          measured-fps
          (config/pattern-name (get-in world [:view :pattern]))
          (if (get-in world [:clock :paused?]) " PAUSED" "")
          (get-in world [:clock :tick])
          (get-in world [:clock :simulation-fps])
          (:particle-speed world)
          (:drift-multiplier world)
          (:name (theme/current-theme world))))

(defn- draw-frame!
  [{:keys [columns rows] :as surface}
   world
   {:keys [measured-fps]}]
  (let [layout (fit-to-window world columns rows)
        palette (palette-for world)
        scene (projection/project-world world)]
    (draw-field! surface world scene layout palette)
    (when (> rows 1)
      (terminal/clear-style! surface)
      (terminal/set-foreground! surface (second (:bands palette)))
      (terminal/put-string-clipped! surface 0 (dec rows) columns footer))
    (when (and (pos? columns) (pos? rows))
      (terminal/set-foreground! surface (last (:bands palette)))
      (terminal/set-bold! surface true)
      (terminal/put-string-right! surface 0
                                  (status-text world measured-fps)))))

(defn- publish! [world]
  (reset! runtime/world world)
  world)

(defn- tick-batch [world tick-count dt]
  (publish! (model/advance-world-steps world tick-count dt)))

(defn- handle-event [world event]
  (let [command (key->command event)]
    (cond
      (= :quit command) terminal/quit
      command (publish! (controller/apply-command world command))
      :else world)))

(defn start!
  "Start the interactive terminal animation."
  []
  (when (runtime/claim-terminal!)
    (try
      (terminal/run-animation!
       {:start (fn [_surface] @runtime/world)
        :tick-batch tick-batch
        :draw! draw-frame!
        :handle-event handle-event
        :simulation-fps #(get-in % [:clock :simulation-fps])
        :render-fps #(get-in % [:config :clock :render-fps])
        :max-frame-delta-ms #(get-in % [:config :clock
                                        :max-frame-delta-ms])
        :finish publish!})
      (finally
        (runtime/release-terminal!)))))

(defn -main [& _args]
  (start!))
