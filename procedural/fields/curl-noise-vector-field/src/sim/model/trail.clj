;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.trail
  "Sparse, semantic cells left behind by moving particles.")

(defn fade
  "Fade visible cells and forget intensities below the render threshold."
  [simulation-config cells seconds]
  (let [factor (Math/exp (* -1.0
                            (get-in simulation-config [:trail :decay])
                            seconds))
        threshold (get-in simulation-config [:render :glow-threshold])]
    (into {}
          (keep (fn [[position cell]]
                  (let [intensity (* factor (:intensity cell))]
                    (when (> intensity threshold)
                      [position (assoc cell :intensity intensity)]))))
          cells)))

(defn deposit
  "Paint particle deposits over a sparse trail. Last particle wins its band."
  [simulation-config cells deposits]
  (let [intensity (get-in simulation-config [:trail :hit-intensity])]
    (reduce (fn [cells {:keys [position band]}]
              (assoc cells position
                     {:position position :intensity intensity :band band}))
            cells
            deposits)))
