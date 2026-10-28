(ns sim.render.lanterna-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [sim.model.core :as model]
   [sim.render.lanterna :as lanterna]))

(deftest terminal-events-use-the-shared-controller-bindings
  (doseq [[character command]
          [[\n :next-pattern]
           [\p :previous-pattern]
           [\t :next-theme]
           [\T :previous-theme]
           [\space :toggle-pause]
           [\r :reset]
           [\+ :faster]
           [\- :slower]
           [(char 93) :raise-sim-fps]
           [(char 91) :lower-sim-fps]]]
    (is (= command
           (lanterna/key->command
            {:type :character :character character}))))
  (is (= :quit (lanterna/key->command {:type :escape})))
  (is (= :quit (lanterna/key->command {:type :eof})))
  (is (= :quit
         (lanterna/key->command
          {:type :character :character (char 3)})))
  (is (nil? (lanterna/key->command
             {:type :character :character \x})))
  (is (nil? (lanterna/key->command
             {:type :key :key :arrow-up}))))

(deftest model-fits-the-terminal-window
  (let [world (model/initial-world)]
    (testing "the field is aspect-correct and reserves two HUD rows"
      (is (= {:columns 71 :rows 20 :left 3 :top 1 :border 1}
             (lanterna/fit-to-window world 80 24))))
    (testing "narrow windows letterbox rather than stretching model space"
      (is (= {:columns 18 :rows 5 :left 0 :top 2 :border 1}
             (lanterna/fit-to-window world 20 12))))
    (testing "wide and tall windows center the same aspect-correct field"
      (is (= [71 20 23 1]
             ((juxt :columns :rows :left :top)
              (lanterna/fit-to-window world 120 24))))
      (is (= [78 22 0 8]
             ((juxt :columns :rows :left :top)
              (lanterna/fit-to-window world 80 40)))))
    (testing "a terminal without interior rows returns an empty field"
      (is (= {:columns 78 :rows 0 :left 0 :top 1 :border 1}
             (lanterna/fit-to-window world 80 4))))))
