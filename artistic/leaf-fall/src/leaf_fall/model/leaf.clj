;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.model.leaf
  "Own pure elapsed-time motion for falling leaves.

  WHY: delayed release, falling position, trail head, and completion rules are
  shared by the phase lifecycle and renderer projection.
  DATA: template and falling-leaf map shapes are documented below.
  OPERATIONS: start, inspect, advance, and retire falling leaves.
  BOUNDARY: tree construction, world phases, grid-cell projection, and drawing
  remain outside this namespace.")

;; Leaf data
;;
;; A template waits in the tree:
;; {:position [row column] :glyph \* :delay-seconds 0.25 :speed 10.0}
;;
;; A falling leaf keeps both its origin and current position:
;; {:origin [row column] :position [floating-row column]
;;  :glyph \* :delay-remaining 0.25 :speed 10.0}

(defn start
  "Turn an immutable leaf template into a falling leaf."
  [{:keys [position glyph delay-seconds speed]}]
  (let [[row column] position]
    {:origin position
     :position [(double row) column]
     :glyph glyph
     :delay-remaining (double delay-seconds)
     :speed (double speed)}))

(defn waiting?
  [falling-leaf]
  (pos? (:delay-remaining falling-leaf)))

(defn advance
  "Advance one leaf, spending its delay before moving."
  [falling-leaf seconds]
  (let [delay (:delay-remaining falling-leaf)
        waiting-seconds (min delay seconds)
        falling-seconds (- seconds waiting-seconds)]
    (-> falling-leaf
        (assoc :delay-remaining (- delay waiting-seconds))
        (update-in [:position 0]
                   + (* (:speed falling-leaf) falling-seconds)))))

(defn head-row
  [falling-leaf]
  (long (Math/floor (double (first (:position falling-leaf))))))

(defn finished?
  [falling-leaf row-count trail-length]
  (> (head-row falling-leaf)
     (+ row-count trail-length)))

(defn advance-leaves
  "Advance all falling leaves and remove completed trails."
  [falling-leaves seconds row-count trail-length]
  (->> falling-leaves
       (map #(advance % seconds))
       (remove #(finished? % row-count trail-length))
       vec))
