;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.model.leaf
  "Pure elapsed-time motion for falling leaf particles.")

(defn start
  "Turn an immutable tree leaf template into a falling particle."
  [{:keys [origin glyph delay-seconds speed]}]
  {:origin origin
   :position-row (double (first origin))
   :column (second origin)
   :glyph glyph
   :delay-remaining (double delay-seconds)
   :speed (double speed)})

(defn waiting? [leaf]
  (pos? (:delay-remaining leaf)))

(defn advance
  "Advance a particle by seconds, spending its delay before moving it."
  [leaf seconds]
  (let [delay (:delay-remaining leaf)
        waiting-seconds (min delay seconds)
        active-seconds (- seconds waiting-seconds)]
    (-> leaf
        (assoc :delay-remaining (- delay waiting-seconds))
        (update :position-row + (* (:speed leaf) active-seconds)))))

(defn head-row [leaf]
  (long (Math/floor (:position-row leaf))))

(defn finished?
  [row-count trail-length leaf]
  (> (head-row leaf) (+ row-count trail-length)))

(defn advance-all
  "Advance particles and discard those whose complete trails left the grid."
  [leaves seconds row-count trail-length]
  (into []
        (comp (map #(advance % seconds))
              (remove #(finished? row-count trail-length %)))
        leaves))
