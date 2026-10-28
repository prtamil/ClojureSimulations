(ns leaf-fall.render.lanterna-test
  (:require
   [clojure.test :refer [deftest is]]
   [leaf-fall.model.core :as model]
   [leaf-fall.render.lanterna :as lanterna]))

(deftest terminal-events-use-controller-bindings
  (doseq [[character command]
          [[\r :new-tree] [\space :skip]
           [(char 93) :raise-sim-fps] [(char 91) :lower-sim-fps]]]
    (is (= command (lanterna/key->command
                    {:type :character :character character}))))
  (is (= :quit (lanterna/key->command {:type :escape})))
  (is (= :quit (lanterna/key->command {:type :eof}))))

(deftest tree-fits-an-aspect-correct-terminal-viewport
  (is (= {:columns 78 :rows 17 :left 0 :top 2 :border 1}
         (lanterna/fit-to-window (model/initial-world) 80 24))))
