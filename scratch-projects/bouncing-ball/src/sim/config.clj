;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.config)

(def supported-band-count 4)

(def default-config
  {:grid
   {:width 40
    :height 20}

   :ball
   {:radius 0.45
    :initial {:x 10.0
              :y 5.0
              :speed 12.0
              :direction (/ Math/PI 4.0)}
    :speed-multiplier {:min 0.25
                       :initial 1.0
                       :max 8.0}}

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
    :background [4 6 12]
    :hud-background [10 13 22]
    :hud-text [220 220 220]
    :boundary [52 58 72]}})

(defn grid-width [config]
  (get-in config [:grid :width]))

(defn grid-height [config]
  (get-in config [:grid :height]))

(defn cell-count [config]
  (* (grid-width config) (grid-height config)))

(defn- finite-number? [value]
  (and (number? value)
       (Double/isFinite (double value))))

(defn- positive-number? [value]
  (and (finite-number? value) (pos? value)))

(defn- byte-value? [value]
  (and (int? value) (<= 0 value 255)))

(defn- rgb-color? [value]
  (and (vector? value)
       (= 3 (count value))
       (every? byte-value? value)))

(defn validate!
  "Return config unchanged, or throw with the path of an invalid setting."
  [config]
  (let [checks
        [[[:grid :width] pos-int? "must be a positive integer"]
         [[:grid :height] pos-int? "must be a positive integer"]
         [[:ball :radius] positive-number? "must be a positive finite number"]
         [[:ball :initial :x] finite-number? "must be a finite number"]
         [[:ball :initial :y] finite-number? "must be a finite number"]
         [[:ball :initial :speed] positive-number? "must be a positive finite number"]
         [[:ball :initial :direction] finite-number? "must be a finite number"]
         [[:ball :speed-multiplier :min] positive-number? "must be positive"]
         [[:ball :speed-multiplier :initial] positive-number? "must be positive"]
         [[:ball :speed-multiplier :max] positive-number? "must be positive"]
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
         [[:render :hud-text] rgb-color? "must be an RGB vector"]
         [[:render :boundary] rgb-color? "must be an RGB vector"]]]
    (doseq [[path valid? message] checks
            :let [value (get-in config path)]
            :when (not (valid? value))]
      (throw (ex-info (str "Invalid simulation configuration at " path ": " message)
                      {:path path :value value})))
    (let [{:keys [min initial max]} (get-in config [:clock :simulation-fps])]
      (when-not (<= min initial max)
        (throw (ex-info "Invalid simulation FPS range"
                        {:path [:clock :simulation-fps]
                         :values [min initial max]}))))
    (let [{:keys [min initial max]} (get-in config [:ball :speed-multiplier])]
      (when-not (<= min initial max)
        (throw (ex-info "Invalid ball speed multiplier range"
                        {:path [:ball :speed-multiplier]
                         :values [min initial max]}))))
    (let [width (double (grid-width config))
          height (double (grid-height config))
          radius (double (get-in config [:ball :radius]))
          x (double (get-in config [:ball :initial :x]))
          y (double (get-in config [:ball :initial :y]))]
      (when-not (< (* 2.0 radius) (min width height))
        (throw (ex-info "Ball diameter must be smaller than both grid dimensions"
                        {:path [:ball :radius]
                         :value radius})))
      (when-not (and (<= radius x (- width radius))
                     (<= radius y (- height radius)))
        (throw (ex-info "Initial ball center must be inside the radius-aware bounds"
                        {:path [:ball :initial]
                         :value {:x x :y y}}))))
    (when-not (= supported-band-count (get-in config [:render :band-count]))
      (throw (ex-info (str "Themes require exactly " supported-band-count
                           " color bands")
                      {:path [:render :band-count]
                       :value (get-in config [:render :band-count])})))
    config))
