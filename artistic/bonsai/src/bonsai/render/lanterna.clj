;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.render.lanterna
  "Immediate-mode terminal adapter over the baked bonsai character canvas."
  (:require
   [bonsai.app.controller :as controller]
   [bonsai.app.runtime :as runtime]
   [bonsai.model.config :as config]
   [bonsai.model.core :as model]
   [bonsai.render.cells :as cells]
   [bonsai.terminal.framework :as terminal]
   [bonsai.utility.theme :as theme]))

(def ^:private footer
  "n/N style  t/T theme  r seed  g grow  +/- wind  [/] Hz  space pause  q quit")

(defn key->command [{:keys [type character]}]
  (case type
    :character (if (= character (char 3))
                 :quit
                 (controller/key-event->command {:raw-key character}))
    :escape :quit
    :eof :quit
    nil))

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

(def ^:private palettes
  (mapv
   (fn [active-theme]
     {:accent (terminal/rgb (:accent active-theme))
      :trunk-dark (terminal/rgb
                   (theme/xterm-rgb (nth (:trunk active-theme) 0)))
      :trunk-mid (terminal/rgb
                  (theme/xterm-rgb (nth (:trunk active-theme) 1)))
      :trunk-light (terminal/rgb
                    (theme/xterm-rgb (nth (:trunk active-theme) 2)))
      :foliage (mapv (comp terminal/rgb theme/xterm-rgb)
                     (:foliage active-theme))
      :pot (terminal/rgb (theme/xterm-rgb (:pot active-theme)))})
   theme/themes))

(defn- cell-color [palette {:keys [role color-index]}]
  (case role
    :trunk-dark (:trunk-dark palette)
    :trunk-mid (:trunk-mid palette)
    :trunk-light (:trunk-light palette)
    :foliage (nth (:foliage palette) (mod color-index 6))
    :snow-tip (nth (:foliage palette) 5)
    :pot (:pot palette)
    (:accent palette)))

(defn- projected-scene [world layout window-columns palette]
  (let [simulation-config (:config world)
        width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)]
    (reduce
     (fn [cells {:keys [row column] :as cell}]
       (if-let [glyph (cells/display-glyph world cell)]
         (let [point (terminal/project-point layout column row width height)
               index (+ (:column point) (* (:row point) window-columns))]
           (assoc cells index
                  {:column (:column point)
                   :row (:row point)
                   :glyph glyph
                   :color (cell-color palette cell)
                   :bold? (#{:foliage :snow-tip} (:role cell))}))
         cells))
     {}
     (vals (:cells world)))))

(defn- draw-scene! [{:keys [columns] :as surface} world layout palette]
  (let [{field-columns :columns field-rows :rows
         left :left top :top border :border} layout]
    (when (and (pos? field-columns) (pos? field-rows))
      (terminal/set-foreground! surface (:accent palette))
      (terminal/draw-box! surface left top
                          (+ field-columns (* 2 border))
                          (+ field-rows (* 2 border)))
      (doseq [[_ {:keys [column row glyph color bold?]}]
              (projected-scene world layout columns palette)]
        (terminal/set-foreground! surface color)
        (terminal/set-bold! surface bold?)
        (terminal/put-character! surface column row glyph)))))

(defn- status-text [world measured-fps]
  (format "%5.1f fps  %s  %s  wind:%d  grow:%d%%  tick:%,d%s"
          measured-fps
          (:name (model/current-style world))
          (:name (theme/current-theme world))
          (:wind world)
          (int (Math/round (* 100.0 (:growth-progress world))))
          (get-in world [:clock :tick])
          (if (get-in world [:clock :paused?]) " PAUSED" "")))

(defn- draw-frame!
  [{:keys [columns rows] :as surface} world {:keys [measured-fps]}]
  (let [palette (nth palettes (:theme-index world))]
    (draw-scene! surface world (fit-to-window world columns rows) palette)
    (when (> rows 1)
      (terminal/clear-style! surface)
      (terminal/set-foreground! surface (:accent palette))
      (terminal/put-string-clipped! surface 0 (dec rows) columns footer))
    (when (and (pos? columns) (pos? rows))
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

(defn start! []
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
