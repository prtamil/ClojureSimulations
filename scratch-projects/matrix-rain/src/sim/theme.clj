;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.theme)

(def themes
  [{:name "MATRIX"  :bands [[0 45 0] [0 120 0] [0 220 55] [210 255 220]] :flash [235 255 235]}
   {:name "CYAN"    :bands [[0 35 55] [0 110 145] [0 220 255] [220 255 255]] :flash [255 255 255]}
   {:name "AMBER"   :bands [[55 25 0] [145 75 0] [255 175 0] [255 245 190]] :flash [255 255 225]}
   {:name "MAGENTA" :bands [[55 0 45] [145 0 120] [255 0 220] [255 220 250]] :flash [255 255 255]}
   {:name "MONO"    :bands [[25 25 25] [90 90 90] [190 190 190] [255 255 255]] :flash [255 255 255]}
   {:name "FIRE"    :bands [[55 0 0] [155 30 0] [255 105 0] [255 245 175]] :flash [255 255 220]}])

(defn current-theme [world]
  (nth themes (:theme-index world)))

(defn cycle-theme [world direction]
  (update world :theme-index #(mod (+ % direction) (count themes))))
