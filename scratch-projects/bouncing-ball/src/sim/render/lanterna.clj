;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.render.lanterna
  "Draw the bouncing-ball model with the reusable terminal framework."
  (:require
   [sim.app.controller :as controller]
   [sim.app.runtime :as runtime]
   [sim.model.config :as config]
   [sim.model.core :as model]
   [sim.terminal.framework :as terminal]
   [sim.utility.theme :as theme]))

(def ^:private footer
  "t/T theme  r reset  space pause  +/- speed  [/] Hz  q/Esc/Ctrl-C quit")

(defn key->command
  "Translate a normalized terminal key into a bouncing-ball command."
  [{:keys [type character]}]
  (case type
    :character (if (= character (char 3))
                 :quit
                 (controller/key-event->command {:raw-key character}))
    :escape :quit
    :eof :quit
    nil))

(defn fit-to-window
  "Fit an aspect-correct, centered viewport into the terminal.

  Terminal cells are usually taller than they are wide. The configured cell
  aspect keeps equal model distances visually equal instead of stretching the
  simulation to every available row and column."
  [world window-columns window-rows]
  (let [simulation-config (:config world)
        grid-width (config/grid-width simulation-config)
        grid-height (config/grid-height simulation-config)
        cell-aspect (get-in simulation-config
                            [:render :terminal-cell-aspect])
        layout (terminal/fit-viewport
                window-columns window-rows grid-width grid-height
                {:cell-aspect cell-aspect
                 :padding-top 1
                 :padding-bottom 1
                 :border 1})
        {:keys [x y]} (:ball world)
        point (terminal/project-point layout x y grid-width grid-height)]
    (cond-> (dissoc layout :border)
      point (assoc :ball-column (:column point)
                   :ball-row (:row point)))))

(def ^:private palettes
  (mapv (fn [{:keys [bands flash]}]
          {:bands (mapv terminal/rgb bands)
           :flash (terminal/rgb flash)})
        theme/themes))

(defn- palette-for [world]
  (nth palettes (:theme-index world)))

(defn- status-text [world measured-fps]
  (format "%5.1f fps  sim:%dHz x%.2f %s%s"
          measured-fps
          (:sim-fps world)
          (:speed-mult world)
          (:name (theme/current-theme world))
          (if (:paused? world) " PAUSED" "")))

(defn- draw-frame!
  [{:keys [columns rows] :as terminal}
   world
   {:keys [alpha dt measured-fps]}]
  (let [window-columns columns
        window-rows rows
        render-world (model/project-world world (* alpha dt))
        layout (fit-to-window render-world window-columns window-rows)
        field-columns (:columns layout)
        field-rows (:rows layout)
        left (:left layout)
        top (:top layout)
        ball-column (:ball-column layout)
        ball-row (:ball-row layout)
        palette (palette-for world)
        [_field-color footer-color ball-color status-color] (:bands palette)]
    (if (and (pos? field-columns) (pos? field-rows))
      (do
        (terminal/set-foreground!
         terminal (terminal/rgb (get-in world [:config :render :boundary])))
        (terminal/draw-box! terminal left top
                            (+ field-columns 2) (+ field-rows 2))
        (terminal/set-foreground! terminal ball-color)
        (terminal/set-bold! terminal true)
        (terminal/put-character! terminal ball-column ball-row \O))
      (when (and (pos? window-columns) (> window-rows 2))
        (terminal/set-foreground! terminal (:flash palette))
        (terminal/put-string-clipped!
         terminal 0 1 window-columns
         "Terminal is too small for the simulation")))

    (when (> window-rows 1)
      (terminal/clear-style! terminal)
      (terminal/set-foreground! terminal footer-color)
      (terminal/put-string-clipped! terminal 0 (dec window-rows)
                                    window-columns footer))

    ;; UI is last because terminal cells use last-write-wins z-order.
    (when (and (pos? window-columns) (pos? window-rows))
      (terminal/set-foreground! terminal status-color)
      (terminal/set-bold! terminal true)
      (terminal/put-string-right! terminal 0
                                  (status-text world measured-fps)))))

(defn- publish! [world]
  (reset! runtime/world world)
  world)

(defn- tick [world dt]
  (publish! (model/update-world world dt)))

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
       {:start (fn [_terminal] @runtime/world)
        :tick tick
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
