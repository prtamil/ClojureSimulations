;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
;;
;; ASCII LEVEL GUIDE
;; -----------------
;; Use ASCII after model updates work as data and before opening Quil. Remember:
;; STATE -> STRING -> INSPECT -> ASSERT. This adapter must stay read-only;
;; simulation rules belong in sim.model, not in glyph or rendering functions.
(ns sim.ascii
  (:require
   [clojure.string :as str]
   [sim.config :as config]
   [sim.theme :as theme]))

(defn- density-glyph [simulation-config glow]
  (let [high-threshold (get-in simulation-config
                               [:render :density-thresholds :high])
        mid-threshold (get-in simulation-config
                              [:render :density-thresholds :mid])
        glow-threshold (get-in simulation-config [:render :glow-threshold])]
    (cond
      (> glow high-threshold) \#
      (> glow mid-threshold) \*
      (> glow glow-threshold) \.
      :else \space)))

(defn- arrow-glyph [simulation-config [vx vy]]
  (let [stationary-threshold (get-in simulation-config
                                     [:views :vector :stationary-threshold])
        axis-dominance (get-in simulation-config [:views :vector :axis-dominance])
        ax (Math/abs (double vx))
        ay (Math/abs (double vy))]
    (cond
      (and (< ax stationary-threshold) (< ay stationary-threshold)) \.
      (> ax (* axis-dominance ay)) (if (pos? vx) \> \<)
      (> ay (* axis-dominance ax)) (if (pos? vy) \v \^)
      (pos? (* vx vy)) \\
      :else \/)))

(defn- particle-preview-cells [world columns rows]
  (let [simulation-config (:config world)
        width (config/grid-width simulation-config)
        height (config/grid-height simulation-config)]
    (when (config/particle-pattern? (:pattern world))
      (into #{}
            (map (fn [{:keys [x y]}]
                   [(min (dec columns) (int (* x (/ columns width))))
                    (min (dec rows) (int (* y (/ rows height))))]))
            (:particles world)))))

(defn render-string
  "Return a compact, headless view. Options default to 80 by 30 characters."
  ([world] (render-string world {}))
  ([world {:keys [columns rows] :or {columns 80 rows 30}}]
   (let [simulation-config (:config world)
         width (config/grid-width simulation-config)
         height (config/grid-height simulation-config)
         columns (min columns width)
         rows (min rows height)
         particles (particle-preview-cells world columns rows)
         {:keys [glow vectors]} (:buffers world)
         header (format "tick:%d  pattern:%s  speed:%d  drift:x%d  theme:%s%s"
                        (:tick world)
                        (config/pattern-name (:pattern world))
                        (:speed world)
                        (:drift-mult world)
                        (:name (theme/current-theme world))
                        (if (:paused? world) "  PAUSED" ""))
         lines
         (for [screen-y (range rows)]
           (apply str
                  (for [screen-x (range columns)]
                    (if (contains? particles [screen-x screen-y])
                      \@
                      (let [x (min (dec width)
                                   (int (* screen-x (/ width columns))))
                            y (min (dec height)
                                   (int (* screen-y (/ height rows))))
                            index (+ x (* y width))]
                        (or (some->> (get vectors index)
                                     (arrow-glyph simulation-config))
                            (density-glyph simulation-config (nth glow index))))))))]
     (str header "\n" (str/join "\n" lines)))))

(defn render [world]
  (println (render-string world)))

(comment
  ;; ASCII GUIDE FOR A REPL PROMPT
  ;; Copy/evaluate this require first. Use :reload after editing these files.
  (require '[clojure.string :as str]
           '[sim.ascii :as ascii]
           '[sim.config :as config]
           '[sim.model :as model]
           '[sim.theme :as theme]
           :reload)

  ;; 1. CREATE and render a reproducible particle world.
  (def before (model/initial-world 42))
  (ascii/render before)

  ;; 2. Prefer render-string when comparing output or writing an assertion.
  (def after
    (model/update-world-steps before 20 (/ 1.0 60.0)))
  (def particle-text
    (ascii/render-string after {:columns 60 :rows 20}))
  (println particle-text)
  (assert (str/includes? particle-text "pattern:PARTICLES"))
  (assert (str/includes? particle-text "tick:20"))

  ;; 3. Render each field view. Updating after the switch prepares the buffer
  ;; for that pattern; changing :pattern alone does not rebuild cached data.
  (def vector-world
    (-> after
        (assoc :pattern :vector)
        model/update-world))
  (ascii/render vector-world)
  (count (get-in vector-world [:buffers :vectors]))

  (def potential-world
    (-> after
        (assoc :pattern :potential)
        model/update-world))
  (ascii/render potential-world)

  (def curl-world
    (-> after
        (assoc :pattern :curl-magnitude)
        model/update-world))
  (ascii/render curl-world)

  (def warped-world
    (-> after
        (assoc :pattern :warped)
        model/update-world))
  (ascii/render warped-world)

  ;; 4. Shrink previews without changing the configured simulation grid.
  (println (ascii/render-string vector-world {:columns 40 :rows 15}))

  ;; 5. Inspect model data beside the picture to explain what a glyph means.
  (select-keys vector-world [:tick :field-time :pattern :buffer-pattern])
  (take 5 (get-in vector-world [:buffers :vectors]))
  (apply max (get-in potential-world [:buffers :glow]))

  ;; 6. Non-physics state should also appear in the header.
  (def themed-world (theme/cycle-theme after 1))
  (assert (str/includes? (ascii/render-string themed-world) "theme:MATRIX"))

  ;; WHEN EXTENDING ASCII
  ;; - derive glyphs only from the supplied world and configuration;
  ;; - keep render-string pure and keep printing inside render only;
  ;; - show every new model view headlessly before adding Quil drawing;
  ;; - test dimensions plus one meaningful glyph or header value.

  ;; Run integration tests containing the ASCII checks.
  (require '[clojure.test :as test]
           '[sim.model-test]
           :reload)
  (test/run-tests 'sim.model-test)
  )

