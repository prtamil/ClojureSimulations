(ns sim.theme-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.config :as config]
   [sim.model :as model]
   [sim.theme :as theme]))

(defn- rgb-color? [value]
  (and (vector? value)
       (= 3 (count value))
       (every? #(and (int? %) (<= 0 % 255)) value)))

(deftest themes-have-valid-complete-palettes
  (is (seq theme/themes))
  (is (= (count theme/themes)
         (count (distinct (map :name theme/themes)))))
  (is (every? #(= config/supported-band-count (count (:bands %)))
              theme/themes))
  (is (every? rgb-color? (mapcat :bands theme/themes)))
  (is (every? #(rgb-color? (:flash %)) theme/themes)))

(deftest active-theme-is-derived-from-world-state
  (let [world (model/initial-world)
        next-world (theme/cycle-theme world 1)]
    (is (= "DEFAULT" (:name (theme/current-theme world))))
    (is (= "MATRIX" (:name (theme/current-theme next-world))))))
