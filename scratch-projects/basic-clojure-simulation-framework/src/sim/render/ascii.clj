;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
;;
;; ASCII LEVEL GUIDE
;; -----------------
;; Use this namespace after the model works as data and before opening Quil.
;; The loop to remember is: STATE -> STRING -> INSPECT -> ASSERT.
;;
;; Keep this adapter read-only. It may derive characters from a world, but it
;; must never advance the model, mutate runtime atoms, or contain physics.
;; `render-string` is the test-friendly boundary; `render` only prints it.
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

(defn place-particles [grid particles]
  (reduce
   (fn [result {:keys [x y]}]
     (assoc-in result [(int (Math/floor (double y)))
                       (int (Math/floor (double x)))] \@))
   grid
   particles))

(defn- preview-particles [world columns rows]
  (let [simulation-config (:config world)
        width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)]
    (into #{}
          (map (fn [{:keys [x y]}]
                 [(min (dec columns) (int (* x (/ columns width))))
                  (min (dec rows) (int (* y (/ rows height))))]))
          (:particles world))))

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
           occupied (preview-particles world columns rows)
           header (format "tick:%d  time:%.2fs  rate:%dHz  seed:%d  theme:%s%s"
                          (:tick world)
                          (:elapsed-seconds world)
                          (:sim-fps world)
                          (:seed world)
                          (:name (theme/current-theme world))
                          (if (:paused? world) "  PAUSED" ""))
           lines (for [y (range rows)]
                   (apply str
                          (for [x (range columns)]
                            (if (contains? occupied [x y]) \@ \.))))]
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

  ;; 1. CREATE a known world. Fixed seeds make failures reproducible.
  (def before (model/initial-world 42))

  ;; 2. TURN the world into a string when debugging or writing tests.
  ;; This is pure: the same world always produces the same text.
  (def before-text (ascii/render-string before))
  before-text
  (println before-text)

  ;; 3. STEP the pure model, then render the returned world.
  ;; Ten ticks at 60 Hz represent one sixth of a simulated second.
  (def after
    (model/update-world-steps before 10 (/ 1.0 60.0)))
  (ascii/render after)

  ;; 4. INSPECT model data beside the picture. ASCII shows spatial behavior;
  ;; ordinary data queries explain why that picture was produced.
  (select-keys after [:tick :elapsed-seconds :sim-fps :theme-index])
  (mapv #(select-keys % [:id :x :y :vx :vy]) (:particles after))

  ;; 5. SHRINK large worlds for quick terminal previews. These options affect
  ;; only the preview; they never alter the world or its configured grid.
  (println (ascii/render-string after {:columns 20 :rows 10}))

  ;; 6. COMPARE snapshots to answer a focused question.
  (= (:particles before) (:particles after)) ; expected: false
  (= before (model/initial-world 42))         ; expected: true

  ;; 7. ASSERT text properties in tests. Prefer this over capturing stdout.
  (str/includes? (ascii/render-string after) "tick:10")
  (str/includes? (ascii/render-string after) "theme:DEFAULT")
  (assert (str/includes? (ascii/render-string after) "tick:10"))

  ;; 8. CHECK non-physics state too. The picture stays the same, while the
  ;; header proves the selected theme is part of the world.
  (-> after
      (theme/cycle-theme 1)
      ascii/render-string
      println)

  ;; WHEN EXTENDING THIS FILE
  ;; - Add a glyph function for each new kind of visible model data.
  ;; - Derive every glyph from the supplied world/configuration.
  ;; - Keep `render-string` pure and keep printing inside `render` only.
  ;; - Add a test that checks both dimensions and one meaningful glyph/header.
  ;; - If ASCII needs to calculate simulation behavior, move that behavior to
  ;;   sim.model.core and let ASCII only display its result.

  ;; Run the integration tests that include ASCII checks.
  (require '[clojure.test :as test]
           '[sim.model.core-test]
           :reload)
  (test/run-tests 'sim.model.core-test)
  )
