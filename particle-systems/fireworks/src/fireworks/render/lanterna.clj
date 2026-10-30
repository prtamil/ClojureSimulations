;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.render.lanterna
  "Immediate-mode terminal adapter matching the original ncurses display."
  (:require
   [fireworks.app.controller :as controller]
   [fireworks.app.runtime :as runtime]
   [fireworks.model.core :as model]
   [fireworks.render.glyphs :as glyphs]
   [fireworks.terminal.framework :as terminal]
   [fireworks.utility.theme :as theme]))

(def ^:private footer
  " q:quit  ]/[:Hz  +/-:rockets  t/T:theme  space:pause  r:reset ")

(defn key->command [{:keys [type character]}]
  (case type
    :character (if (= character (char 3))
                 :quit
                 (controller/key-event->command {:raw-key character}))
    :escape :quit
    :eof :quit
    nil))

(defn- scale-rgb [rgb factor]
  (mapv #(int (Math/round (* % factor))) rgb))

(def ^:private palettes
  (mapv (fn [{:keys [spark-colors]}]
          (let [colors (mapv (comp terminal/rgb theme/xterm-rgb)
                             spark-colors)
                dim-colors (mapv (comp terminal/rgb
                                       #(scale-rgb % 0.42)
                                       theme/xterm-rgb)
                                 spark-colors)]
            {:colors colors :dim-colors dim-colors}))
        theme/themes))

(def ^:private hud-color (terminal/rgb theme/hud-rgb))
(def ^:private hint-color (terminal/rgb theme/hint-rgb))

(defn- glyph-color [palette {:keys [color-index dim?]}]
  (nth (if dim? (:dim-colors palette) (:colors palette))
       (mod color-index 7)))

(defn- draw-show! [surface world]
  (let [palette (nth palettes (:theme-index world))]
    (doseq [{:keys [column row glyph bold?] :as render-glyph}
            (glyphs/project-show world)]
      (terminal/set-foreground! surface (glyph-color palette render-glyph))
      (terminal/set-bold! surface bold?)
      (terminal/put-character! surface column row glyph))))

(defn status-text [world measured-fps]
  (format " %5.1f fps  sim:%2d Hz  rkt:%2d%s "
          measured-fps
          (get-in world [:clock :simulation-fps])
          (model/firework-count world)
          (if (get-in world [:clock :paused?]) " PAUSED" "")))

(defn theme-text [world]
  (format " theme:%s " (:name (theme/current-theme world))))

(defn- draw-hud!
  [{:keys [columns rows] :as surface} world measured-fps]
  (when (and (pos? columns) (pos? rows))
    (terminal/clear-style! surface)
    (terminal/set-foreground! surface hud-color)
    (terminal/set-bold! surface true)
    (terminal/put-string-right! surface 0 (status-text world measured-fps))
    (when (> rows 1)
      (terminal/set-bold! surface false)
      (terminal/put-string-right! surface 1 (theme-text world)))
    (terminal/set-foreground! surface hint-color)
    (terminal/set-bold! surface true)
    (terminal/put-string-clipped! surface 0 (dec rows) columns footer)))

(defn- draw-frame! [surface world {:keys [measured-fps]}]
  (draw-show! surface world)
  (draw-hud! surface world measured-fps))

(defn- publish! [world]
  (reset! runtime/world world)
  world)

(defn- size-world [world {:keys [columns rows]}]
  (publish! (model/resize-world world columns rows)))

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
       {:start (fn [surface] (size-world @runtime/world surface))
        :resize size-world
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
