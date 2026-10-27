;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
;;
;; ASCII LEVEL GUIDE
;; -----------------
;; Use this adapter before Quil: STATE -> STRING -> INSPECT -> ASSERT.
(ns sim.ascii
  (:require
   [clojure.string :as str]
   [sim.config :as config]
   [sim.theme :as theme]))

(defn empty-grid
  ([] (empty-grid config/default-config))
  ([simulation-config]
   (vec (repeat (config/grid-height simulation-config)
                (vec (repeat (config/grid-width simulation-config) \.))))))

(defn place-balls [grid balls]
  (reduce
   (fn [result {:keys [x y]}]
     (let [row (int (Math/floor (double y)))
           column (int (Math/floor (double x)))]
       (update-in result [row column]
                  (fn [current]
                    (if (= \. current) \o \*)))))
   grid
   balls))

(defn- preview-cell [world columns rows {:keys [x y]}]
  (let [simulation-config (:config world)
        width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)]
    [(min (dec columns) (int (* x (/ columns width))))
     (min (dec rows) (int (* y (/ rows height))))]))

(defn render-string
  "Return a headless view. `o` is one ball and `*` is a shared preview cell."
  ([world] (render-string world {}))
  ([world {:keys [columns rows]}]
   (let [simulation-config (:config world)
         width (config/grid-width simulation-config)
         height (config/grid-height simulation-config)
         columns (or columns width)
         rows (or rows height)]
     (when-not (and (pos-int? columns) (pos-int? rows))
       (throw (ex-info "ASCII dimensions must be positive integers"
                       {:columns columns :rows rows})))
     (let [columns (min columns width)
           rows (min rows height)
           occupied (frequencies
                     (map (partial preview-cell world columns rows)
                          (:balls world)))
           header (format (str "tick:%d  time:%.2fs  balls:%d  speed:x%.2f  "
                               "theme:%s%s")
                          (:tick world)
                          (:elapsed-seconds world)
                          (count (:balls world))
                          (:speed-mult world)
                          (:name (theme/current-theme world))
                          (if (:paused? world) "  PAUSED" ""))
           lines (for [y (range rows)]
                   (apply str
                          (for [x (range columns)
                                :let [occupants (get occupied [x y] 0)]]
                            (case occupants
                              0 \.
                              1 \o
                              \*))))]
       (str header "\n" (str/join "\n" lines))))))

(defn render [world]
  (println (render-string world)))

(comment
  ;; ASCII GUIDE FOR A REPL PROMPT
  (require '[clojure.string :as str]
           '[sim.ascii :as ascii]
           '[sim.model :as model]
           '[sim.theme :as theme]
           :reload)

  (def before (model/initial-world 42))
  (ascii/render before)

  (def after
    (model/update-world-steps before 30 (/ 1.0 60.0)))
  (ascii/render after)

  ;; Pure strings are easier to compare and assert than captured stdout.
  (def after-text (ascii/render-string after))
  (assert (str/includes? after-text "tick:30"))
  (assert (str/includes? after-text "balls:12"))

  ;; A compact view may merge balls into `*`; it does not alter the world.
  (println (ascii/render-string after {:columns 20 :rows 10}))

  ;; Inspect exact positions when two glyphs appear close together.
  (mapv #(select-keys % [:id :x :y :vx :vy :radius]) (:balls after))

  (def themed-world (theme/cycle-theme after 1))
  (assert (str/includes? (ascii/render-string themed-world) "theme:MATRIX"))

  ;; Keep render-string pure, printing in render only, and collision rules in
  ;; sim.model. Add ASCII evidence and tests before adding graphical behavior.

  (require '[clojure.test :as test]
           '[sim.model-test]
           :reload)
  (test/run-tests 'sim.model-test)
  )
