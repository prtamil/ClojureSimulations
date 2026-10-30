;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.render.ascii
  "Headless world-to-text renderer for REPL work and tests."
  (:require
   [clojure.string :as str]
   [fireworks.model.config :as config]
   [fireworks.model.core :as model]
   [fireworks.render.glyphs :as glyphs]
   [fireworks.utility.theme :as theme]))

(defn render-string [world]
  (let [simulation-config (:config world)
        width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)
        canvas (reduce (fn [canvas {:keys [row column glyph]}]
                         (assoc-in canvas [row column] glyph))
                       (vec (repeat height (vec (repeat width \space))))
                        (glyphs/project-show world))
        header (format (str "FIREWORKS  theme:%s  sim:%d Hz  rockets:%d  "
                            "tick:%,d%s")
                       (:name (theme/current-theme world))
                        (get-in world [:clock :simulation-fps])
                        (model/firework-count world)
                        (get-in world [:clock :tick])
                        (if (get-in world [:clock :paused?]) "  PAUSED" ""))]
    (str header "\n" (str/join "\n" (map #(apply str %) canvas)))))

(defn render [world]
  (println (render-string world)))
