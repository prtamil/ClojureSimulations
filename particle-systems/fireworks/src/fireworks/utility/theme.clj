;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.utility.theme)

;; These are the exact ten xterm-256 palettes from fireworks.c.
(def themes
  [{:name "matrix" :spark-colors [28 34 40 46 82 118 154]}
   {:name "neon" :spark-colors [201 207 165 51 87 45 213]}
   {:name "nova" :spark-colors [196 202 208 220 226 231 255]}
   {:name "ocean" :spark-colors [33 39 45 51 87 123 195]}
   {:name "fire" :spark-colors [196 202 208 214 220 226 230]}
   {:name "toxic" :spark-colors [46 82 118 154 190 226 220]}
   {:name "gold" :spark-colors [130 136 172 178 214 220 230]}
   {:name "ice" :spark-colors [33 39 45 51 87 123 159]}
   {:name "aurora" :spark-colors [46 82 51 87 165 201 207]}
   {:name "plasma" :spark-colors [93 99 165 201 207 51 87]}])

(def hud-rgb [255 255 0])
(def hint-rgb [0 255 255])

(defn xterm-rgb [palette-index]
  (cond
    (< palette-index 16)
    (nth [[0 0 0] [128 0 0] [0 128 0] [128 128 0]
          [0 0 128] [128 0 128] [0 128 128] [192 192 192]
          [128 128 128] [255 0 0] [0 255 0] [255 255 0]
          [0 0 255] [255 0 255] [0 255 255] [255 255 255]]
         palette-index)

    (< palette-index 232)
    (let [cube-index (- palette-index 16)
          levels [0 95 135 175 215 255]
          red (quot cube-index 36)
          green (quot (mod cube-index 36) 6)
          blue (mod cube-index 6)]
      [(nth levels red) (nth levels green) (nth levels blue)])

    :else
    (let [gray (+ 8 (* 10 (- palette-index 232)))]
      [gray gray gray])))

(defn current-theme [world]
  (nth themes (:theme-index world)))

(defn cycle-index [index direction]
  (mod (+ index direction) (count themes)))

(defn spark-rgb [world color-index]
  (-> (current-theme world)
      :spark-colors
      (nth (mod color-index 7))
      xterm-rgb))
