;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.projection
  "Translate rain streams into renderer-independent visible glyphs.

  WHY: ASCII, terminal, and Quil share the same clipping and stream-depth rule.
  DATA: each result is {:position [column row] :depth n :stream-length n
  :character ch}; this namespace retains no state.
  OPERATIONS: derive the visible glyph collection from an immutable world.
  BOUNDARY: screen scaling, terminal cells, colors, drawing, and stream
  evolution remain outside this namespace."
  (:require [sim.model.config :as config]))

(defn visible-glyphs [world]
  (let [width (config/grid-width (:config world))
        height (config/grid-height (:config world))]
    (vec
     (for [{:keys [column head-y glyphs]} (:drops world)
           [depth character] (map-indexed vector glyphs)
           :let [row (int (Math/floor (- head-y depth)))]
           :when (and (<= 0 column (dec width))
                      (<= 0 row (dec height)))]
       {:position [column row]
        :depth depth
        :stream-length (count glyphs)
        :character character}))))
