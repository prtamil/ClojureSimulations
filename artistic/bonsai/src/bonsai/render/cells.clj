;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.render.cells
  "Project persistent grid cells into frame-specific visible cells.

  WHY: ASCII, terminal, and Quil share growth reveal and deterministic wind
  animation, while the persistent raster must remain stable.
  DATA: consumes actual discrete grid cells and returns cells with display
  glyphs; retains no state.
  OPERATIONS: test visibility, select a display glyph, and collect visible cells.
  BOUNDARY: geometry, rasterization, clock advancement, and device drawing stay
  outside this namespace."
  (:require
   [bonsai.model.config :as config]
   [bonsai.utility.random :as random]
   [bonsai.utility.theme :as theme]))

(defn- foliage-reveal-threshold [world row column]
  (let [jitter (/ (mod (random/hash3 column row (:seed world)) 100) 100.0)]
    (+ 0.70 (* 0.30 jitter))))

(defn- branch-reveal-threshold [world row]
  (let [height (config/grid-height (:config world))
        height-fraction (/ (- (dec height) row)
                           (double (max 1 (dec height))))]
    (* 0.78 height-fraction)))

(defn visible?
  "Return true after the growth animation reaches a semantic cell."
  [world {:keys [role row column]}]
  (let [progress (:growth-progress world)]
    (cond
      (= role :pot) true
      (>= progress 1.0) true
      (#{:foliage :snow-tip} role)
      (>= progress (foliage-reveal-threshold world row column))
      :else
      (>= progress (branch-reveal-threshold world row)))))

(defn- rustled-glyph [world row column original-glyph]
  (let [leaf-glyphs (:leaves (theme/current-theme world))
        base-frequency (get-in world [:config :animation :rustle-frequency])
        base-rate (get-in world [:config :animation :rustle-rate])
        wind (:wind world)
        time-key (int (* (get-in world [:clock :elapsed-seconds])
                         base-frequency
                         (+ 0.4 (* wind 0.6))))
        hash-value (random/hash3 column row time-key)
        flutter-threshold (long (* 255.0
                                   (min 0.45 (* base-rate wind))))]
    (if (< (bit-and hash-value 0xff) flutter-threshold)
      (nth leaf-glyphs
           (mod (unsigned-bit-shift-right hash-value 8)
                (count leaf-glyphs)))
      original-glyph)))

(defn display-glyph
  "Return a cell's glyph for this frame, or nil while it remains hidden."
  [world {:keys [glyph role row column] :as cell}]
  (when (and cell (visible? world cell))
    (if (and (= role :foliage) (pos? (:wind world)))
      (rustled-glyph world row column glyph)
      glyph)))

(defn visible-cells
  "Return semantic cells with their frame-specific display glyphs."
  [world]
  (into []
        (keep (fn [cell]
                (when-let [glyph (display-glyph world cell)]
                  (assoc cell :glyph glyph))))
        (vals (:cells world))))
