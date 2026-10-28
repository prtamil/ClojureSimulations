(ns sim.render.lanterna-test
  (:require
   [clojure.test :refer [deftest is]]
   [sim.model.core :as model]
   [sim.render.lanterna :as lanterna]))

(deftest terminal-events-use-controller-bindings
  (doseq [[character command]
          [[\t :next-theme] [\T :previous-theme] [\space :toggle-pause]
           [\r :reset] [\+ :faster] [\- :slower]
           [(char 93) :raise-sim-fps] [(char 91) :lower-sim-fps]]]
    (is (= command (lanterna/key->command
                    {:type :character :character character}))))
  (is (= :quit (lanterna/key->command {:type :escape})))
  (is (= :quit (lanterna/key->command
                {:type :character :character (char 3)}))))

(deftest model-fits-an-aspect-correct-terminal-viewport
  (is (= {:columns 78 :rows 20 :left 0 :top 1 :border 1}
         (lanterna/fit-to-window (model/initial-world) 80 24))))
