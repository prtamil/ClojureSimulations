;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.render.ascii
  "Render an immutable world as a compact, testable text preview.

  WHY: headless runs and tests need a pure view without opening a terminal or
  Quil window. DATA: the renderer reads the world directly because no shared
  projection policy is needed. OPERATIONS: render a string or print it.
  BOUNDARY: this namespace never advances or mutates the simulation."
  (:require
   [clojure.string :as str]
   [sim.model.config :as config]
   [sim.utility.theme :as theme]))

(defn- preview-particle-positions [world columns rows]
  (let [simulation-config (:config world)
        width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)]
    (into #{}
          (map (fn [{:keys [x y]}]
                 [(min (dec columns) (int (* x (/ columns width))))
                  (min (dec rows) (int (* y (/ rows height))))]))
          (:particles world))))

(defn render-string
  "Return a headless view. Options default to the configured grid size."
  ([world]
   (render-string world {}))
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
           occupied (preview-particle-positions world columns rows)
           header (format "tick:%d  time:%.2fs  rate:%dHz  seed:%d  theme:%s%s"
                          (get-in world [:clock :tick])
                          (get-in world [:clock :elapsed-seconds])
                          (get-in world [:clock :simulation-fps])
                          (:seed world)
                          (:name (theme/current-theme world))
                          (if (get-in world [:clock :paused?])
                            "  PAUSED"
                            ""))
           lines (for [y (range rows)]
                   (apply str
                          (for [x (range columns)]
                            (if (contains? occupied [x y]) \@ \.))))]
       (str header "\n" (str/join "\n" lines))))))

(defn render [world]
  (println (render-string world)))
