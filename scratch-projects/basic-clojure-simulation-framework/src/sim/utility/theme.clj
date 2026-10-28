;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.utility.theme)

;; Themes contain visual data only. The model stores just :theme-index, keeping
;; renderer policy out of simulation rules while making theme changes part of
;; the same inspectable world state.
(def themes
  [{:name "DEFAULT" :bands [[0 0 95] [0 135 255] [255 215 0] [255 255 255]] :flash [255 255 0]}
   {:name "MATRIX"  :bands [[0 95 0] [0 175 0] [0 255 0] [135 255 95]] :flash [255 255 0]}
   {:name "NOVA"    :bands [[95 0 95] [175 0 255] [255 0 215] [255 175 215]] :flash [255 255 0]}
   {:name "MONO"    :bands [[28 28 28] [128 128 128] [208 208 208] [238 238 238]] :flash [255 255 0]}
   {:name "OCEAN"   :bands [[0 0 95] [0 135 255] [0 215 255] [0 255 255]] :flash [255 255 0]}
   {:name "FIRE"    :bands [[95 0 0] [175 0 0] [255 135 0] [255 255 0]] :flash [255 0 0]}
   {:name "EARTH"   :bands [[95 95 0] [135 135 0] [215 135 95] [255 255 215]] :flash [255 255 0]}
   {:name "FOREST"  :bands [[0 95 0] [0 135 0] [95 135 0] [175 215 0]] :flash [255 255 0]}
   {:name "DESERT"  :bands [[135 95 0] [175 135 0] [215 135 95] [255 215 135]] :flash [255 255 0]}
   {:name "ARCTIC"  :bands [[0 0 135] [0 215 255] [175 215 255] [255 255 255]] :flash [255 255 0]}])

(defn current-theme [world]
  (nth themes (:theme-index world)))

(defn cycle-theme [world direction]
  (update world :theme-index #(mod (+ % direction) (count themes))))
