;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.render.cells
  "Project renderer-independent model values onto character cells."
  (:require
   [fireworks.model.config :as config]))

(defn- in-bounds? [simulation-config column row]
  (and (<= 0 column) (< column (config/grid-width simulation-config))
       (<= 0 row) (< row (config/grid-height simulation-config))))

(defn- put-cell [cells simulation-config column row cell]
  (if (in-bounds? simulation-config column row)
    ;; Later rockets and sparks overwrite earlier values, matching a painter's
    ;; algorithm without leaking renderer concerns into the simulation model.
    (assoc cells [column row] (assoc cell :column column :row row))
    cells))

(defn- ascending-cells [cells simulation-config rocket]
  (let [[x y] (:position rocket)
        column (int x)
        row (int y)
        color-index (:color-index rocket)]
    (-> cells
        (put-cell simulation-config column row
                  {:glyph \| :role :rocket :color-index color-index
                   :life 1.0 :bold? true :dim? false})
        (put-cell simulation-config column (inc row)
                  {:glyph \' :role :exhaust :color-index color-index
                   :life 0.5 :bold? false :dim? false}))))

(defn- bursting-cells [cells simulation-config rocket]
  (reduce
   (fn [cells {:keys [position glyph color-index life]}]
     (let [[x y] position]
       (put-cell cells simulation-config (int x) (int y)
                 {:glyph glyph :role :spark :color-index color-index
                  :life life :bold? (> life 0.6) :dim? (< life 0.2)})))
   cells
   (:sparks rocket)))

(defn visible-cells
  "Return the final visible character cells for one immutable world."
  [world]
  (let [simulation-config (:config world)]
    (->> (:rockets world)
         (reduce (fn [cells rocket]
                   (case (:phase rocket)
                     :ascending (ascending-cells cells simulation-config rocket)
                     :bursting (bursting-cells cells simulation-config rocket)
                     cells))
                 {})
         vals
         (sort-by (juxt :row :column))
         vec)))
