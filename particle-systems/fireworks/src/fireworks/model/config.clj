;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns fireworks.model.config)

;; Domain values use physical units: terminal cells, seconds, and cells per
;; second. At the default 30 Hz these produce the same motion and fade timing
;; as the original artwork without coupling behavior to a particular tick rate.
(def default-config
  {:grid {:width 96
          :height 38}

   :rockets {:count {:min 1 :initial 6 :max 20 :step 1}
             :launch-speeds [18.0 24.0 30.0 36.0 42.0 48.0]
             :upward-acceleration 29.4
             :burst-ceiling 2.0
             :initial-launch-interval-seconds (/ 8.0 30.0)
             :new-launch-delay-seconds (/ 5.0 30.0)
             :relaunch-delay-seconds {:min 0.5 :max 2.5}}

   :sparks {:per-burst 80
            :speed {:min 12.0 :max 40.0}
            :direction-jitter 0.3
            :vertical-speed-scale 0.5
            :gravity 32.0
            :life {:min 0.6 :max 1.0}
            :fade-rate {:min 0.9 :max 2.1}
            :symbols (vec "*+.,`'^-~=o#@%&$!|\\/:;")}

   :clock {:simulation-fps {:min 10 :initial 30 :max 60 :step 5}
           :render-fps 60
           :max-frame-delta-ms 100}

   :render {:cell-width 10
            :cell-height 18
            :font-size 17
            :hud-height 42
            :footer-height 28
            :background [0 0 0]
            :hud-text [255 255 0]
            :hint-text [0 255 255]}})

(defn grid-width [simulation-config]
  (get-in simulation-config [:grid :width]))

(defn grid-height [simulation-config]
  (get-in simulation-config [:grid :height]))

(defn rocket-limit [simulation-config]
  (get-in simulation-config [:rockets :count :max]))

(defn- finite-number? [value]
  (and (number? value) (Double/isFinite (double value))))

(defn- positive-number? [value]
  (and (finite-number? value) (pos? value)))

(defn- non-negative-number? [value]
  (and (finite-number? value) (not (neg? value))))

(defn- byte-value? [value]
  (and (int? value) (<= 0 value 255)))

(defn- rgb-color? [value]
  (and (vector? value) (= 3 (count value)) (every? byte-value? value)))

(defn- non-empty-positive-vector? [value]
  (and (vector? value) (seq value) (every? positive-number? value)))

(defn- check-value! [simulation-config path valid? message]
  (let [value (get-in simulation-config path)]
    (when-not (valid? value)
      (throw (ex-info (str "Invalid fireworks configuration at " path
                           ": " message)
                      {:path path :value value})))))

(defn- check-ordered-range! [simulation-config path allow-zero?]
  (let [{:keys [min max] :as value} (get-in simulation-config path)
        lower-valid? (if allow-zero? non-negative-number? positive-number?)]
    (when-not (and (map? value)
                   (lower-valid? min)
                   (positive-number? max)
                   (< (double min) (double max)))
      (throw (ex-info (str "Invalid fireworks range at " path)
                      {:path path :value value})))))

(defn validate!
  "Return config unchanged, or throw ex-info describing an invalid setting."
  [simulation-config]
  (doseq [[path valid? message]
          [[[:grid :width] #(and (int? %) (<= 4 %))
            "must be an integer of at least 4"]
           [[:grid :height] #(and (int? %) (<= 4 %))
            "must be an integer of at least 4"]
           [[:rockets :count :min] pos-int? "must be a positive integer"]
           [[:rockets :count :initial] pos-int? "must be a positive integer"]
           [[:rockets :count :max] pos-int? "must be a positive integer"]
           [[:rockets :count :step] pos-int? "must be a positive integer"]
           [[:rockets :launch-speeds] non-empty-positive-vector?
            "must be a non-empty vector of positive finite speeds"]
           [[:rockets :upward-acceleration] positive-number?
            "must be positive and finite"]
           [[:rockets :burst-ceiling] non-negative-number?
            "must be non-negative and finite"]
           [[:rockets :initial-launch-interval-seconds] non-negative-number?
            "must be non-negative and finite"]
           [[:rockets :new-launch-delay-seconds] non-negative-number?
            "must be non-negative and finite"]
           [[:sparks :per-burst] pos-int? "must be a positive integer"]
           [[:sparks :direction-jitter] non-negative-number?
            "must be non-negative and finite"]
           [[:sparks :vertical-speed-scale] positive-number?
            "must be positive and finite"]
           [[:sparks :gravity] non-negative-number?
            "must be non-negative and finite"]
           [[:sparks :symbols] #(and (vector? %) (seq %) (every? char? %))
            "must be a non-empty vector of characters"]
           [[:clock :simulation-fps :min] pos-int? "must be a positive integer"]
           [[:clock :simulation-fps :initial] pos-int? "must be a positive integer"]
           [[:clock :simulation-fps :max] pos-int? "must be a positive integer"]
           [[:clock :simulation-fps :step] pos-int? "must be a positive integer"]
           [[:clock :render-fps] pos-int? "must be a positive integer"]
           [[:clock :max-frame-delta-ms] pos-int? "must be a positive integer"]
           [[:render :cell-width] pos-int? "must be a positive integer"]
           [[:render :cell-height] pos-int? "must be a positive integer"]
           [[:render :font-size] pos-int? "must be a positive integer"]
           [[:render :hud-height] nat-int? "must be a non-negative integer"]
           [[:render :footer-height] nat-int? "must be a non-negative integer"]
           [[:render :background] rgb-color? "must be an RGB byte vector"]
           [[:render :hud-text] rgb-color? "must be an RGB byte vector"]
           [[:render :hint-text] rgb-color? "must be an RGB byte vector"]]]
    (check-value! simulation-config path valid? message))

  (doseq [[path allow-zero?]
          [[[:rockets :relaunch-delay-seconds] true]
           [[:sparks :speed] true]
           [[:sparks :life] false]
           [[:sparks :fade-rate] false]]]
    (check-ordered-range! simulation-config path allow-zero?))

  (let [{count-min :min initial :initial count-max :max}
        (get-in simulation-config [:rockets :count])]
    (when-not (<= count-min initial count-max)
      (throw (ex-info "Invalid rocket-count range"
                      {:path [:rockets :count]
                       :values [count-min initial count-max]}))))

  (let [{fps-min :min initial :initial fps-max :max}
        (get-in simulation-config [:clock :simulation-fps])]
    (when-not (<= fps-min initial fps-max)
      (throw (ex-info "Invalid simulation FPS range"
                      {:path [:clock :simulation-fps]
                       :values [fps-min initial fps-max]}))))

  (when-not (< (get-in simulation-config [:rockets :burst-ceiling])
               (grid-height simulation-config))
    (throw (ex-info "Rocket burst ceiling must fit inside the grid"
                    {:path [:rockets :burst-ceiling]})))

  simulation-config)
