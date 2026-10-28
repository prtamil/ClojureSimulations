;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.utility.theme)

(def themes
  [{:name "SPRING" :trunk [130 137 173]
    :foliage [28 34 64 70 112 154] :pot 136
    :bare? false :leaves (vec "&%#*o^") :snow-tip nil
    :background [5 10 8] :accent [154 255 95]}
   {:name "SUMMER" :trunk [130 137 173]
    :foliage [22 28 34 64 70 112] :pot 136
    :bare? false :leaves (vec "&%#@*o") :snow-tip nil
    :background [4 9 7] :accent [70 215 95]}
   {:name "AUTUMN" :trunk [88 130 166]
    :foliage [124 130 166 202 208 220] :pot 130
    :bare? false :leaves (vec "&%#*@^") :snow-tip nil
    :background [12 7 4] :accent [255 175 0]}
   {:name "WINTER" :trunk [60 66 103]
    :foliage [240 244 247 250 252 255] :pot 103
    :bare? true :leaves (vec ".,oO") :snow-tip \*
    :background [5 8 14] :accent [215 235 255]}
   {:name "CHERRY" :trunk [88 130 166]
    :foliage [175 211 213 217 219 225] :pot 130
    :bare? false :leaves (vec "&%#*o^") :snow-tip nil
    :background [12 6 11] :accent [255 175 215]}
   {:name "MONO" :trunk [245 250 255]
    :foliage [246 248 250 252 253 255] :pot 250
    :bare? false :leaves (vec "&%#*o^") :snow-tip nil
    :background [7 7 8] :accent [238 238 238]}])

(defn xterm-rgb
  "Convert an xterm-256 palette index to an RGB vector."
  [palette-index]
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

(defn cell-rgb
  "Resolve a baked cell's semantic role to an RGB color."
  [active-theme {:keys [role color-index]}]
  (xterm-rgb
   (case role
     :trunk-dark (nth (:trunk active-theme) 0)
     :trunk-mid (nth (:trunk active-theme) 1)
     :trunk-light (nth (:trunk active-theme) 2)
     :foliage (nth (:foliage active-theme) (mod color-index 6))
     :snow-tip (nth (:foliage active-theme) 5)
     :pot (:pot active-theme)
     15)))
