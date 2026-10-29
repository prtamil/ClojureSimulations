;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.render.lanterna
  "Immediate-mode terminal adapter for the procedural leaf-fall scene."
  (:require
   [clojure.string :as str]
   [leaf-fall.app.controller :as controller]
   [leaf-fall.app.runtime :as runtime]
   [leaf-fall.model.config :as config]
   [leaf-fall.model.core :as model]
   [leaf-fall.render.cells :as cells]
   [leaf-fall.terminal.framework :as terminal]
   [leaf-fall.utility.theme :as theme]))

(def ^:private footer
  "r new tree  space skip phase  [/] simulation Hz  q/Esc/Ctrl-C quit")

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

(def ^:private role-colors
  (into {} (map (fn [[role rgb]] [role (terminal/rgb rgb)]))
        theme/role-colors))

(defn- projected-scene [world layout window-columns]
  (let [simulation-config (:config world)
        width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)]
    (reduce
     (fn [cells {:keys [row column glyph role]}]
       (let [point (terminal/project-point layout column row width height)
             index (+ (:column point) (* (:row point) window-columns))]
         (assoc cells index
                {:column (:column point)
                 :row (:row point)
                 :glyph glyph
                 :color (get role-colors role)
                 :bold? (= :fall-head role)})))
     {}
     (cells/visible-cells world))))

(defn- draw-scene! [{:keys [columns] :as surface} world layout]
  (let [{field-columns :columns field-rows :rows
         left :left top :top border :border} layout]
    (when (and (pos? field-columns) (pos? field-rows))
      (terminal/set-foreground! surface (get role-colors :leaf))
      (terminal/draw-box! surface left top
                          (+ field-columns (* 2 border))
                          (+ field-rows (* 2 border)))
      (doseq [[_ {:keys [column row glyph color bold?]}]
              (projected-scene world layout columns)]
        (terminal/set-foreground! surface color)
        (terminal/set-bold! surface bold?)
        (terminal/put-character! surface column row glyph)))))

(defn- status-text [world measured-fps]
  (format "%5.1f fps  %s  cycle:%,d  leaves:%,d/%,d  %dHz  tick:%,d"
          measured-fps
          (str/upper-case (model/phase-name world))
          (:cycle world)
          (model/active-leaf-count world)
          (count (:leaf-templates world))
          (:sim-fps world)
          (:tick world)))

(defn- draw-frame!
  [{:keys [columns rows] :as surface} world {:keys [measured-fps]}]
  (draw-scene! surface world (fit-to-window world columns rows))
  (when (> rows 1)
    (terminal/clear-style! surface)
    (terminal/set-foreground! surface (get role-colors :fall-trail))
    (terminal/put-string-clipped! surface 0 (dec rows) columns footer))
  (when (and (pos? columns) (pos? rows))
    (terminal/set-foreground! surface (get role-colors :fall-head))
    (terminal/set-bold! surface true)
    (terminal/put-string-right! surface 0
                                (status-text world measured-fps))))

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

(defn start! []
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
