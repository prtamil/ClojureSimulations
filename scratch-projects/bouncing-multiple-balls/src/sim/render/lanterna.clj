;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.render.lanterna
  "Immediate-mode terminal adapter for the multiple-ball simulation."
  (:require
   [sim.app.controller :as controller]
   [sim.app.runtime :as runtime]
   [sim.model.config :as config]
   [sim.model.core :as model]
   [sim.terminal.framework :as terminal]
   [sim.utility.theme :as theme]))

(def ^:private footer
  "t/T theme  r reset  space pause  +/- speed  [/] Hz  q/Esc quit")

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

(defn fit-to-window
  "Fit the physical model aspect between the two terminal HUD rows."
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

(defn- projected-balls [world layout window-columns]
  (let [simulation-config (:config world)
        width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)]
    (reduce
     (fn [cells {:keys [x y color]}]
       (if-let [{:keys [column row]}
                (terminal/project-point layout x y width height)]
         (let [index (+ column (* row window-columns))]
           (assoc cells index
                  {:column column
                   :row row
                   :glyph (if (contains? cells index) \* \o)
                   :color color}))
         cells))
     {}
     (:balls world))))

(defn- draw-field! [{:keys [columns] :as surface} world layout palette]
  (let [{field-columns :columns field-rows :rows
         left :left top :top border :border} layout]
    (when (and (pos? field-columns) (pos? field-rows))
      (terminal/set-foreground!
       surface (terminal/rgb (get-in world [:config :render :boundary])))
      (terminal/draw-box! surface left top
                          (+ field-columns (* 2 border))
                          (+ field-rows (* 2 border)))
      (doseq [[_ {:keys [column row glyph color]}]
              (projected-balls world layout columns)]
        (terminal/set-foreground! surface (nth (:bands palette) color))
        (terminal/set-bold! surface true)
        (terminal/put-character! surface column row glyph)))))

(defn- status-text [world measured-fps]
  (format "%5.1f fps  balls:%d  tick:%,d  %dHz  x%.2f  %s%s"
          measured-fps
          (count (:balls world))
          (:tick world)
          (:sim-fps world)
          (:speed-mult world)
          (:name (theme/current-theme world))
          (if (:paused? world) " PAUSED" "")))

(defn- draw-frame!
  [{:keys [columns rows] :as surface} world {:keys [measured-fps]}]
  (let [palette (nth palettes (:theme-index world))
        layout (fit-to-window world columns rows)]
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
  "Start the interactive multiple-ball terminal animation."
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
