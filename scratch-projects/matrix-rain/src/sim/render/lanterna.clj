;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.render.lanterna
  "Immediate-mode terminal adapter for deterministic Matrix Rain."
  (:require
   [sim.app.controller :as controller]
   [sim.app.runtime :as runtime]
   [sim.model.config :as config]
   [sim.model.core :as model]
   [sim.model.projection :as projection]
   [sim.terminal.framework :as terminal]
   [sim.utility.theme :as theme]))

(def ^:private footer
  "t/T theme  r reset  space pause  +/- speed  [/] Hz  q/Esc/Ctrl-C quit")

(defn key->command
  "Translate a normalized terminal event through the shared controller."
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

(defn fit-to-window [world window-columns window-rows]
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

(defn- trail-color [palette depth length]
  (let [bands (:bands palette)
        progress (/ depth (double (max 1 (dec length))))
        index (-> (* (- 1.0 progress) (dec (count bands)))
                  Math/floor int (max 0) (min (dec (count bands))))]
    (nth bands index)))

(defn- projected-cells [world layout window-columns palette]
  (let [simulation-config (:config world)
        width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)]
    (reduce
     (fn [cells {:keys [position depth stream-length character] :as glyph}]
       (let [[x y] position
             point (terminal/project-point layout x y width height)
             index (+ (:column point) (* (:row point) window-columns))
             current (get cells index)]
         (if (or (nil? current) (< depth (:depth current)))
           (assoc cells index
                  (assoc glyph
                         :column (:column point)
                         :row (:row point)
                         :glyph character
                         :color (if (zero? depth)
                                  (:flash palette)
                                  (trail-color palette depth stream-length))))
           cells)))
     {}
     (projection/visible-glyphs world))))

(defn- draw-rain! [{:keys [columns] :as surface} world layout palette]
  (let [{field-columns :columns field-rows :rows
         left :left top :top border :border} layout]
    (when (and (pos? field-columns) (pos? field-rows))
      (terminal/set-foreground! surface (first (:bands palette)))
      (terminal/draw-box! surface left top
                          (+ field-columns (* 2 border))
                          (+ field-rows (* 2 border)))
      (doseq [[_ {:keys [column row glyph color depth]}]
              (projected-cells world layout columns palette)]
        (terminal/set-foreground! surface color)
        (terminal/set-bold! surface (zero? depth))
        (terminal/put-character! surface column row glyph)))))

(defn- status-text [world measured-fps]
  (format "%5.1f fps  streams:%d  tick:%,d  %dHz  speed:x%.2f  %s%s"
          measured-fps
          (count (:drops world))
          (get-in world [:clock :tick])
          (get-in world [:clock :simulation-fps])
          (:speed-multiplier world)
          (:name (theme/current-theme world))
          (if (get-in world [:clock :paused?]) " PAUSED" "")))

(defn- draw-frame!
  [{:keys [columns rows] :as surface} world {:keys [measured-fps]}]
  (let [palette (nth palettes (:theme-index world))]
    (draw-rain! surface world (fit-to-window world columns rows) palette)
    (when (> rows 1)
      (terminal/clear-style! surface)
      (terminal/set-foreground! surface (second (:bands palette)))
      (terminal/put-string-clipped! surface 0 (dec rows) columns footer))
    (when (and (pos? columns) (pos? rows))
      (terminal/set-foreground! surface (:flash palette))
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
  "Start the interactive Matrix Rain terminal animation."
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
