;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.config)

(def supported-band-count 4)

(def default-config
  {:grid
   {:width 40
    :height 20}

   :rain
   {:drop-count 15
    :charset "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz@#$%&*+-<>"
    :length {:min 5
             :max 14}
    :speed {:min 8.0
            :max 22.0}
    :speed-multiplier {:min 0.25
                       :initial 1.0
                       :max 8.0}
    :spawn-depth 20
    :glyph-change-probability 0.08}

   :clock
   {:simulation-fps {:min 10
                     :initial 60
                     :max 240
                     :step 10}
    :render-fps 60
    :max-frame-delta-ms 100}

   :noise
   {:scale 0.05
    :fbm-octaves 3}

   :render
   {:cell-size 15
    :hud-height 58
    :footer-height 30
    :band-count supported-band-count
    :background [0 2 0]
    :hud-background [2 10 4]
    :hud-text [190 230 195]}})

(defn grid-width [config]
  (get-in config [:grid :width]))

(defn grid-height [config]
  (get-in config [:grid :height]))

(defn cell-count [config]
  (* (grid-width config) (grid-height config)))

(defn- finite-number? [value]
  (and (number? value) (Double/isFinite (double value))))

(defn- positive-number? [value]
  (and (finite-number? value) (pos? value)))

(defn- unit-interval? [value]
  (and (finite-number? value) (<= 0.0 value 1.0)))

(defn- byte-value? [value]
  (and (int? value) (<= 0 value 255)))

(defn- rgb-color? [value]
  (and (vector? value) (= 3 (count value))
       (every? byte-value? value)))

(defn validate!
  "Return config unchanged, or throw with the path of an invalid setting."
  [config]
  (let [checks
        [[[:grid :width] pos-int? "must be a positive integer"]
         [[:grid :height] pos-int? "must be a positive integer"]
         [[:rain :drop-count] pos-int? "must be a positive integer"]
         [[:rain :charset] #(and (string? %) (seq %)) "must be a non-empty string"]
         [[:rain :length :min] pos-int? "must be a positive integer"]
         [[:rain :length :max] pos-int? "must be a positive integer"]
         [[:rain :speed :min] positive-number? "must be positive"]
         [[:rain :speed :max] positive-number? "must be positive"]
         [[:rain :speed-multiplier :min] positive-number? "must be positive"]
         [[:rain :speed-multiplier :initial] positive-number? "must be positive"]
         [[:rain :speed-multiplier :max] positive-number? "must be positive"]
         [[:rain :spawn-depth] pos-int? "must be a positive integer"]
         [[:rain :glyph-change-probability] unit-interval?
          "must be between zero and one"]
         [[:clock :simulation-fps :min] pos-int? "must be a positive integer"]
         [[:clock :simulation-fps :initial] pos-int? "must be a positive integer"]
         [[:clock :simulation-fps :max] pos-int? "must be a positive integer"]
         [[:clock :simulation-fps :step] pos-int? "must be a positive integer"]
         [[:clock :render-fps] pos-int? "must be a positive integer"]
         [[:clock :max-frame-delta-ms] pos-int? "must be a positive integer"]
         [[:noise :scale] positive-number? "must be a positive finite number"]
         [[:noise :fbm-octaves] pos-int? "must be a positive integer"]
         [[:render :cell-size] pos-int? "must be a positive integer"]
         [[:render :hud-height] nat-int? "must be a non-negative integer"]
         [[:render :footer-height] nat-int? "must be a non-negative integer"]
         [[:render :band-count] pos-int? "must be a positive integer"]
         [[:render :background] rgb-color? "must be an RGB vector"]
         [[:render :hud-background] rgb-color? "must be an RGB vector"]
         [[:render :hud-text] rgb-color? "must be an RGB vector"]]]
    (doseq [[path valid? message] checks
            :let [value (get-in config path)]
            :when (not (valid? value))]
      (throw (ex-info (str "Invalid simulation configuration at " path ": " message)
                      {:path path :value value})))
    (doseq [path [[:rain :length]
                  [:rain :speed]
                  [:rain :speed-multiplier]
                  [:clock :simulation-fps]]
            :let [{:keys [min initial max]} (get-in config path)
                  ordered? (if (some? initial)
                             (<= min initial max)
                             (<= min max))]
            :when (not ordered?)]
      (throw (ex-info (str "Invalid configuration range at " path)
                      {:path path :value (get-in config path)})))
    (when-not (= supported-band-count (get-in config [:render :band-count]))
      (throw (ex-info (str "Themes require exactly " supported-band-count
                           " color bands")
                      {:path [:render :band-count]
                       :value (get-in config [:render :band-count])})))
    config))
