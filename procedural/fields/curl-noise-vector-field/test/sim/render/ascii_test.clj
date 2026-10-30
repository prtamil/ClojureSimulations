(ns sim.render.ascii-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [sim.model.config :as config]
   [sim.model.core :as model]
   [sim.render.ascii :as ascii]))

(defn- select-view [world pattern]
  (loop [world world]
    (if (= pattern (get-in world [:view :pattern]))
      world
      (recur (model/cycle-view world 1)))))

(defn- signature [text]
  [(.hashCode ^String text) (count text)])

(deftest ascii-render-has-one-line-per-preview-row
  (let [text (-> (model/initial-world 42)
                 model/advance-world
                 (ascii/render-string {:columns 40 :rows 12}))]
    (is (re-find #"pattern:PARTICLES" text))
    (is (= 13 (count (str/split-lines text))))))

(deftest custom-grids-limit-the-preview-size
  (let [small-config (-> config/default-config
                         (assoc-in [:grid :width] 24)
                         (assoc-in [:grid :height] 12)
                         (assoc-in [:particles :count] 16))
        lines (-> (model/initial-world small-config 42)
                  ascii/render-string
                  str/split-lines)]
    (is (= 13 (count lines)))
    (is (every? #(= 24 (count %)) (rest lines)))))

(deftest seeded-views-preserve-their-original-pictures
  (let [expected {:particles [-2021105549 2490]
                  :vector [890833322 2487]
                  :potential [946036264 2490]
                  :curl-magnitude [-235988160 2489]
                  :warped [-853986809 2487]}]
    (doseq [pattern config/pattern-order]
      (testing (name pattern)
        (let [world (-> (model/initial-world 42)
                        (select-view pattern)
                        (model/advance-world-steps 60 (/ 1.0 60.0)))
              text (ascii/render-string world {:columns 80 :rows 30})]
          (is (= (get expected pattern) (signature text))))))))
