;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.config)

(def supported-band-count 4)

(def default-config
  {:grid
   {:width 40
    :height 20}

   :balls
   {:count 12
    :radius {:min 0.40
             :max 0.75}
    :speed {:min 4.0
            :max 10.0}
    :speed-multiplier {:min 0.25
                       :initial 1.0
                       :max 8.0}
    :spawn-attempts 500}

   :physics
   {:substeps 4
    :collision-iterations 2
    :restitution 1.0}

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

(defn- unit-interval? [value]
  (and (finite-number? value) (<= 0.0 value 1.0)))

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
         [[:balls :count] pos-int? "must be a positive integer"]
         [[:balls :radius :min] positive-number? "must be positive"]
         [[:balls :radius :max] positive-number? "must be positive"]
         [[:balls :speed :min] positive-number? "must be positive"]
         [[:balls :speed :max] positive-number? "must be positive"]
         [[:balls :speed-multiplier :min] positive-number? "must be positive"]
         [[:balls :speed-multiplier :initial] positive-number? "must be positive"]
         [[:balls :speed-multiplier :max] positive-number? "must be positive"]
         [[:balls :spawn-attempts] pos-int? "must be a positive integer"]
         [[:physics :substeps] pos-int? "must be a positive integer"]
         [[:physics :collision-iterations] pos-int? "must be a positive integer"]
         [[:physics :restitution] unit-interval? "must be between zero and one"]
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
    (doseq [path [[:balls :radius]
                  [:balls :speed]
                  [:balls :speed-multiplier]
                  [:clock :simulation-fps]]
            :let [{:keys [min initial max]} (get-in config path)
                  ordered? (if (some? initial)
                             (<= min initial max)
                             (<= min max))]
            :when (not ordered?)]
      (throw (ex-info (str "Invalid configuration range at " path)
                      {:path path :value (get-in config path)})))
    (let [diameter (* 2.0 (get-in config [:balls :radius :max]))]
      (when-not (< diameter (min (grid-width config) (grid-height config)))
        (throw (ex-info "Maximum ball diameter must fit inside the grid"
                        {:path [:balls :radius :max]
                         :value (get-in config [:balls :radius :max])}))))
    (when-not (= supported-band-count (get-in config [:render :band-count]))
      (throw (ex-info (str "Themes require exactly " supported-band-count
                           " color bands")
                      {:path [:render :band-count]
                       :value (get-in config [:render :band-count])})))
    config))
