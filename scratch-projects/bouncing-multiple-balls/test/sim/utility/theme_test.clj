(ns sim.utility.theme-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.model.config :as config]
   [sim.model.core :as model]
   [sim.utility.theme :as theme]))

(defn- rgb-color? [value]
  (and (vector? value) (= 3 (count value))
       (every? #(and (int? %) (<= 0 % 255)) value)))

(deftest themes-have-valid-complete-palettes
  (is (= (count theme/themes) (count (distinct (map :name theme/themes)))))
  (is (every? #(= config/supported-band-count (count (:bands %)))
              theme/themes))
  (is (every? rgb-color? (mapcat :bands theme/themes)))
  (is (every? #(rgb-color? (:flash %)) theme/themes)))

(deftest active-theme-is-world-data
  (let [world (model/initial-world)]
    (is (= "DEFAULT" (:name (theme/current-theme world))))
    (is (= "MATRIX" (:name (theme/current-theme
                             (theme/cycle-theme world 1)))))))
