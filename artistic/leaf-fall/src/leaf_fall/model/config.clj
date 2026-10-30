;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns leaf-fall.model.config
  "Own adjustable leaf-fall settings and staged validation.

  WHY: growth, rasterization, phases, clocks, and renderers need one trusted
  settings boundary. DATA: grid, tree, fall, phase, clock, and render rules.
  OPERATIONS: dimension access and named validation stages.
  BOUNDARY: live world values and derived geometry do not belong here.")

(def default-config
  {:grid {:width 96
          :height 42}
   :tree {:max-depth 7
          :trunk-height-min 0.45
          :trunk-height-max 0.65
          :branch-spread 0.50
          :foliage-aspect 0.25
          :foliage-density-base 0.62
          :foliage-density-falloff 0.33}
   :fall {:trail-length 7
          :start-delay-max-seconds 4.4
          :speed-options [18.1818181818 9.0909090909 6.0606060606]}
   :phase {:display-seconds 2.5
           :reset-seconds 0.7}
   :clock {:simulation-fps {:min 20 :initial 60 :max 120 :step 10}
           :render-fps 30
           :max-frame-delta-ms 100}
   :render {:cell-width 10
            :cell-height 15
            :terminal-cell-aspect 2.0
            :font-size 15
            :hud-height 74
            :footer-height 34
            :background [2 5 3]
            :panel-background [8 15 11]
            :hud-text [226 232 228]
            :hud-accent [120 255 115]
            :hint [70 220 235]}})

(defn grid-width [simulation-config]
  (get-in simulation-config [:grid :width]))

(defn grid-height [simulation-config]
  (get-in simulation-config [:grid :height]))

(defn cell-count [simulation-config]
  (* (grid-width simulation-config) (grid-height simulation-config)))

(defn- finite-number? [value]
  (and (number? value) (Double/isFinite (double value))))

(defn- positive-number? [value]
  (and (finite-number? value) (pos? (double value))))

(defn- probability? [value]
  (and (finite-number? value) (<= 0.0 (double value) 1.0)))

(defn- rgb? [value]
  (and (vector? value)
       (= 3 (count value))
       (every? #(and (int? %) (<= 0 % 255)) value)))

(defn- positive-number-vector? [value]
  (and (vector? value) (seq value) (every? positive-number? value)))

(defn- check! [simulation-config path predicate message]
  (let [value (get-in simulation-config path)]
    (when-not (predicate value)
      (throw (ex-info (str "Invalid leaf-fall configuration at " path ": " message)
                      {:path path :value value})))))

(def ^:private value-checks
  [[[:grid :width] #(and (int? %) (<= 24 % 320))
    "must be an integer from 24 through 320"]
   [[:grid :height] #(and (int? %) (<= 12 % 128))
    "must be an integer from 12 through 128"]
   [[:tree :max-depth] pos-int? "must be a positive integer"]
   [[:tree :trunk-height-min] probability? "must be in [0, 1]"]
   [[:tree :trunk-height-max] probability? "must be in [0, 1]"]
   [[:tree :branch-spread] positive-number? "must be positive and finite"]
   [[:tree :foliage-aspect] positive-number? "must be positive and finite"]
   [[:tree :foliage-density-base] probability? "must be in [0, 1]"]
   [[:tree :foliage-density-falloff] probability? "must be in [0, 1]"]
   [[:fall :trail-length] nat-int? "must be a non-negative integer"]
   [[:fall :start-delay-max-seconds] positive-number?
    "must be positive and finite"]
   [[:fall :speed-options] positive-number-vector?
    "must be a non-empty vector of positive finite speeds"]
   [[:phase :display-seconds] positive-number?
    "must be positive and finite"]
   [[:phase :reset-seconds] positive-number?
    "must be positive and finite"]
   [[:clock :simulation-fps :min] pos-int? "must be a positive integer"]
   [[:clock :simulation-fps :initial] pos-int? "must be a positive integer"]
   [[:clock :simulation-fps :max] pos-int? "must be a positive integer"]
   [[:clock :simulation-fps :step] pos-int? "must be a positive integer"]
   [[:clock :render-fps] pos-int? "must be a positive integer"]
   [[:clock :max-frame-delta-ms] pos-int? "must be a positive integer"]
   [[:render :cell-width] pos-int? "must be a positive integer"]
   [[:render :cell-height] pos-int? "must be a positive integer"]
   [[:render :terminal-cell-aspect] positive-number?
    "must be positive and finite"]
   [[:render :font-size] pos-int? "must be a positive integer"]
   [[:render :hud-height] nat-int? "must be a non-negative integer"]
   [[:render :footer-height] nat-int? "must be a non-negative integer"]
   [[:render :background] rgb? "must be an RGB byte vector"]
   [[:render :panel-background] rgb? "must be an RGB byte vector"]
   [[:render :hud-text] rgb? "must be an RGB byte vector"]
   [[:render :hud-accent] rgb? "must be an RGB byte vector"]
   [[:render :hint] rgb? "must be an RGB byte vector"]])

(defn- validate-values!
  [simulation-config]
  (doseq [[path predicate message] value-checks]
    (check! simulation-config path predicate message))
  simulation-config)

(defn- validate-tree-limits!
  [simulation-config]
  (let [minimum (get-in simulation-config [:tree :trunk-height-min])
        maximum (get-in simulation-config [:tree :trunk-height-max])]
    (when-not (< minimum maximum)
      (throw (ex-info "Minimum trunk height must be below maximum"
                      {:path [:tree :trunk-height]
                       :values [minimum maximum]}))))
  simulation-config)

(defn- validate-clock!
  [simulation-config]
  (let [{:keys [min initial max]}
        (get-in simulation-config [:clock :simulation-fps])]
    (when-not (<= min initial max)
      (throw (ex-info "Initial simulation FPS must be inside its range"
                      {:path [:clock :simulation-fps]
                       :values [min initial max]}))))
  simulation-config)

(defn validate!
  "Return a valid configuration unchanged, or throw ex-info with its bad path."
  [simulation-config]
  (-> simulation-config
      validate-values!
      validate-tree-limits!
      validate-clock!))
