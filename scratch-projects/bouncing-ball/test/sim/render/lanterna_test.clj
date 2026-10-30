(ns sim.render.lanterna-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [sim.model.core :as model]
   [sim.render.lanterna :as lanterna]))

(deftest terminal-events-use-the-shared-controller-bindings
  (is (= :toggle-pause
         (lanterna/key->command
          {:type :character :character \space})))
  (is (= :next-theme
         (lanterna/key->command {:type :character :character \t})))
  (is (= :previous-theme
         (lanterna/key->command {:type :character :character \T})))
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
    (testing "the animation fills a naturally proportioned terminal"
      (is (= {:columns 78 :rows 20 :left 0 :top 1
              :ball-column 21 :ball-row 7}
             (lanterna/fit-to-window world 80 24))))
    (testing "narrow windows letterbox instead of stretching model space"
      (let [{:keys [columns rows left top ball-column ball-row]}
            (lanterna/fit-to-window world 20 12)]
        (is (= [18 5] [columns rows]))
        (is (= [0 2] [left top]))
        (is (< left ball-column (+ left columns 1)))
        (is (< top ball-row (+ top rows 1)))))
    (testing "wide and tall windows center an aspect-correct viewport"
      (is (= [80 20 19 1]
             ((juxt :columns :rows :left :top)
              (lanterna/fit-to-window world 120 24))))
      (is (= [78 20 0 9]
             ((juxt :columns :rows :left :top)
              (lanterna/fit-to-window world 80 40)))))
    (testing "a terminal without room inside the boundary is empty"
      (is (= {:columns 78 :rows 0 :left 0 :top 1}
             (lanterna/fit-to-window world 80 4))))
    (testing "screen coordinates include the left and top boundary offsets"
      (is (= [21 7]
             ((juxt :ball-column :ball-row)
              (lanterna/fit-to-window world 80 24)))))))

(deftest rendering-projects-between-fixed-simulation-ticks
  (let [world (assoc-in (model/initial-world) [:clock :simulation-fps] 10)
        at-tick (lanterna/fit-to-window world 80 24)
        near-next-tick (lanterna/fit-to-window
                        (model/project-world world 0.09) 80 24)]
    (is (< (:ball-column at-tick)
           (:ball-column near-next-tick)))))
