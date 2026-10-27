;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.config)

(def supported-band-count 4)

(def default-config
  {:grid
   {:width 40
    :height 20}

   :particles
   {:initial [{:id 1 :x 5.0 :y 10.0 :vx 8.0 :vy 0.0}
              {:id 2 :x 20.0 :y 5.0 :vx 0.0 :vy 6.0}]}

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
    :hud-height 54
    :footer-height 30
    :band-count supported-band-count
    :background [245 245 245]
    :hud-background [24 27 34]
    :hud-text [235 235 235]}})

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

(defn- valid-particle? [width height particle]
  (and (map? particle)
       (some? (:id particle))
       (every? finite-number? (map particle [:x :y :vx :vy]))
       (<= 0.0 (double (:x particle)))
       (< (double (:x particle)) width)
       (<= 0.0 (double (:y particle)))
       (< (double (:y particle)) height)))

(defn validate!
  "Return config unchanged, or throw with the path of an invalid setting."
  [config]
  (let [checks
        [[[:grid :width] pos-int? "must be a positive integer"]
         [[:grid :height] pos-int? "must be a positive integer"]
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
    (let [{:keys [min initial max]} (get-in config [:clock :simulation-fps])]
      (when-not (<= min initial max)
        (throw (ex-info "Invalid simulation FPS range"
                        {:path [:clock :simulation-fps]
                         :values [min initial max]}))))
    (let [particles (get-in config [:particles :initial])
          width (grid-width config)
          height (grid-height config)]
      (when-not (and (vector? particles)
                     (seq particles)
                     (every? #(valid-particle? width height %) particles))
        (throw (ex-info "Initial particles must be valid and inside the grid"
                        {:path [:particles :initial]
                         :value particles})))
      (when-not (= (count particles) (count (distinct (map :id particles))))
        (throw (ex-info "Initial particle IDs must be unique"
                        {:path [:particles :initial]
                         :value particles}))))
    (when-not (= supported-band-count (get-in config [:render :band-count]))
      (throw (ex-info (str "Themes require exactly " supported-band-count
                           " color bands")
                      {:path [:render :band-count]
                       :value (get-in config [:render :band-count])})))
    config))
