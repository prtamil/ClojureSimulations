;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.render.lanterna
  "Immediate-mode terminal adapter for the curl-noise simulation."
  (:require
   [sim.app.controller :as controller]
   [sim.app.runtime :as runtime]
   [sim.model.config :as config]
   [sim.model.core :as model]
   [sim.render.ascii :as ascii]
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

(defn- model-cell
  [world field-columns field-rows screen-x screen-y]
  (let [simulation-config (:config world)
        width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)
        x (min (dec width)
               (int (* (+ screen-x 0.5) (/ width field-columns))))
        y (min (dec height)
               (int (* (+ screen-y 0.5) (/ height field-rows))))]
    (if (= :vector (:pattern world))
      (let [[lattice-x lattice-y] (get-in simulation-config
                                          [:views :vector :lattice])]
        [(- x (mod x lattice-x))
         (- y (mod y lattice-y))])
      [x y])))

(defn- particle-cells [world layout window-columns]
  (when (config/particle-pattern? (:pattern world))
    (let [simulation-config (:config world)
          width (config/grid-width simulation-config)
          height (config/grid-height simulation-config)]
      (persistent!
       (reduce
        (fn [cells {[x y] :position band :band}]
          (if-let [{:keys [column row]}
                   (terminal/project-point layout x y width height)]
            (assoc! cells (+ column (* row window-columns)) band)
            cells))
        (transient {})
        (:particles world))))))

(defn- cell-appearance
  [world palette particle-cells window-columns screen-column screen-row
   model-x model-y]
  (let [simulation-config (:config world)
        {:keys [intensity band velocity]}
        (get (:cells world) [model-x model-y]
             {:intensity 0.0 :band 0})
        particle-band (get particle-cells
                           (+ screen-column (* screen-row window-columns)))
        glow-threshold (get-in simulation-config [:render :glow-threshold])
        high-threshold (get-in simulation-config
                               [:render :density-thresholds :high])
        sparkle-mask (get-in simulation-config [:flash :sparkle-mask])
        flash? (and (> (:flash-intensity world) glow-threshold)
                    (zero? (bit-and (bit-xor model-x model-y)
                                    sparkle-mask)))]
    (cond
      flash?
      [\* (:flash palette) true]

      (some? particle-band)
      [\@ (nth (:bands palette) particle-band) true]

      (= :vector (:pattern world))
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
  [{:keys [columns] :as surface} world layout palette]
  (let [{field-columns :columns field-rows :rows
         left :left top :top border :border} layout
        particle-cells (particle-cells world layout columns)]
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
                (model-cell world field-columns field-rows screen-x screen-y)]
            (when-let [[glyph color bold?]
                       (cell-appearance world palette particle-cells columns
                                        screen-column screen-row model-x model-y)]
              (terminal/set-foreground! surface color)
              (terminal/set-bold! surface bold?)
              (terminal/put-character! surface screen-column screen-row
                                       glyph))))))))

(defn- status-text [world measured-fps]
  (format "%5.1f fps  %s%s  %,d  %dHz  speed:%d drift:x%d  %s"
          measured-fps
          (config/pattern-name (:pattern world))
          (if (:paused? world) " PAUSED" "")
          (:tick world)
          (:sim-fps world)
          (:speed world)
          (:drift-mult world)
          (:name (theme/current-theme world))))

(defn- draw-frame!
  [{:keys [columns rows] :as surface}
   world
   {:keys [measured-fps]}]
  (let [layout (fit-to-window world columns rows)
        palette (palette-for world)]
    (draw-field! surface world layout palette)
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
  (publish! (model/update-world-steps world tick-count dt)))

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
        :simulation-fps #(:sim-fps %)
        :render-fps #(get-in % [:config :clock :render-fps])
        :max-frame-delta-ms #(get-in % [:config :clock
                                        :max-frame-delta-ms])
        :finish publish!})
      (finally
        (runtime/release-terminal!)))))

(defn -main [& _args]
  (start!))
