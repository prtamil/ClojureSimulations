;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
;;
;; ASCII LEVEL GUIDE
;; -----------------
;; Use ASCII after model updates work as data and before opening Quil. Remember:
;; STATE -> STRING -> INSPECT -> ASSERT. This adapter stays read-only.
(ns sim.render.ascii
  (:require
   [clojure.string :as str]
   [sim.model.config :as config]
   [sim.utility.theme :as theme]))

(defn empty-grid
  ([] (empty-grid config/default-config))
  ([simulation-config]
   (vec (repeat (config/grid-height simulation-config)
                (vec (repeat (config/grid-width simulation-config) \.))))))

(defn place-ball [grid {:keys [x y]}]
  (assoc-in grid [(int (Math/floor (double y)))
                  (int (Math/floor (double x)))] \O))

(defn- preview-cell [world columns rows]
  (let [simulation-config (:config world)
        width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)
        {:keys [x y]} (:ball world)]
    [(min (dec columns) (int (* x (/ columns width))))
     (min (dec rows) (int (* y (/ rows height))))]))

(defn render-string
  "Return a headless view. Options default to the configured grid size."
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
           [ball-x ball-y] (preview-cell world columns rows)
           {:keys [x y vx vy]} (:ball world)
           header (format (str "tick:%d  time:%.2fs  pos:(%.2f,%.2f)  "
                               "vel:(%.2f,%.2f)  speed:x%.2f  theme:%s%s")
                          (:tick world)
                          (:elapsed-seconds world)
                          x y vx vy
                          (:speed-mult world)
                          (:name (theme/current-theme world))
                          (if (:paused? world) "  PAUSED" ""))
           lines (for [screen-y (range rows)]
                   (apply str
                          (for [screen-x (range columns)]
                            (if (= [screen-x screen-y] [ball-x ball-y])
                              \O
                              \.))))]
       (str header "\n" (str/join "\n" lines))))))

(defn render [world]
  (println (render-string world)))

(comment
  ;; ASCII GUIDE FOR A REPL PROMPT
  ;; Copy/evaluate this require first. Use :reload after editing these files.
  (require '[clojure.string :as str]
           '[sim.render.ascii :as ascii]
           '[sim.model.core :as model]
           '[sim.utility.theme :as theme]
           :reload)

  ;; 1. Create a known world and print its headless view.
  (def before (model/initial-world 42))
  (ascii/render before)

  ;; 2. Step the pure model, then render the returned value.
  (def after
    (model/update-world-steps before 30 (/ 1.0 60.0)))
  (ascii/render after)

  ;; 3. Prefer render-string for comparisons and tests.
  (def after-text (ascii/render-string after))
  (assert (str/includes? after-text "tick:30"))
  (assert (str/includes? after-text "theme:DEFAULT"))

  ;; 4. Shrink a preview without changing the configured simulation grid.
  (println (ascii/render-string after {:columns 20 :rows 10}))

  ;; 5. Inspect data beside the picture to explain its movement.
  (select-keys after [:tick :elapsed-seconds :speed-mult :theme-index])
  (select-keys (:ball after) [:x :y :vx :vy :radius])

  ;; 6. Confirm non-physics world state appears in the header.
  (def themed-world (theme/cycle-theme after 1))
  (assert (str/includes? (ascii/render-string themed-world) "theme:MATRIX"))

  ;; WHEN EXTENDING ASCII
  ;; - derive characters only from the supplied world and config;
  ;; - keep render-string pure and keep printing inside render only;
  ;; - add a headless view before adding new graphical behavior;
  ;; - test dimensions and one meaningful glyph or header value.

  ;; Run model and ASCII integration tests from a development REPL.
  (require '[clojure.test :as test]
           '[sim.model.core-test]
           :reload)
  (test/run-tests 'sim.model.core-test)
  )
