(ns bonsai.render.lanterna-test
  (:require
   [bonsai.model.core :as model]
   [bonsai.render.lanterna :as lanterna]
   [clojure.test :refer [deftest is]]))

(deftest terminal-events-use-controller-bindings
  (doseq [[character command]
          [[\n :next-style] [\N :previous-style]
           [\t :next-theme] [\T :previous-theme]
           [\r :reseed] [\g :replay-growth]
           [\+ :stronger-wind] [\- :weaker-wind]
           [\space :toggle-pause]]]
    (is (= command (lanterna/key->command
                    {:type :character :character character}))))
  (is (= :quit (lanterna/key->command {:type :escape}))))

(deftest tree-fits-an-aspect-correct-terminal-viewport
  (is (= {:columns 78 :rows 17 :left 0 :top 2 :border 1}
         (lanterna/fit-to-window (model/initial-world) 80 24))))
