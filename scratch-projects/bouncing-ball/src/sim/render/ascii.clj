;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.render.ascii
  "Project a world directly to deterministic text for terminals and tests.

  This renderer owns only character placement and header formatting; it never
  advances the world or owns simulation state."
  (:require
   [clojure.string :as str]
   [sim.model.config :as config]
   [sim.utility.theme :as theme]))

(defn- preview-cell [world columns rows]
  (let [simulation-config (:config world)
        width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)
        {:keys [x y]} (:ball world)]
    [(min (dec columns) (int (* x (/ columns width))))
     (min (dec rows) (int (* y (/ rows height))))]))

(defn render-string
  "Return a headless view. Options default to the configured grid size."
  ([world] (render-string world {}))
  ([world {:keys [columns rows]}]
   (let [simulation-config (:config world)
         width (config/grid-width simulation-config)
         height (config/grid-height simulation-config)
         columns (or columns width)
         rows (or rows height)]
     (when-not (and (pos-int? columns) (pos-int? rows))
       (throw (ex-info "ASCII dimensions must be positive integers"
                       {:columns columns :rows rows})))
     (let [columns (min columns width)
           rows (min rows height)
           [ball-x ball-y] (preview-cell world columns rows)
           {:keys [x y vx vy]} (:ball world)
           header (format (str "tick:%d  time:%.2fs  pos:(%.2f,%.2f)  "
                               "vel:(%.2f,%.2f)  speed:x%.2f  theme:%s%s")
                          (get-in world [:clock :tick])
                          (get-in world [:clock :elapsed-seconds])
                          x y vx vy
                          (:speed-multiplier world)
                          (:name (theme/current-theme world))
                          (if (get-in world [:clock :paused?]) "  PAUSED" ""))
           lines (for [screen-y (range rows)]
                   (apply str
                          (for [screen-x (range columns)]
                            (if (= [screen-x screen-y] [ball-x ball-y])
                              \O
                              \.))))]
       (str header "\n" (str/join "\n" lines))))))

(defn render [world]
  (println (render-string world)))

(comment
  (require '[clojure.string :as str]
           '[sim.render.ascii :as ascii]
           '[sim.model.core :as model]
           '[sim.utility.theme :as theme]
           :reload)

  (def before (model/initial-world 42))
  (ascii/render before)

  (def after
    (model/advance-world-steps before 30 (/ 1.0 60.0)))
  (ascii/render after)

  (def after-text (ascii/render-string after))
  (assert (str/includes? after-text "tick:30"))
  (assert (str/includes? after-text "theme:DEFAULT"))

  (println (ascii/render-string after {:columns 20 :rows 10}))

  (select-keys (:clock after) [:tick :elapsed-seconds :paused?])
  (select-keys after [:speed-multiplier :theme-index])
  (select-keys (:ball after) [:x :y :vx :vy :radius])

  (def themed-world (theme/cycle-theme after 1))
  (assert (str/includes? (ascii/render-string themed-world) "theme:MATRIX"))
  )
