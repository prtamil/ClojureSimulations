;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.model.config)

(def style-order [:chokkan :moyogi :shakan :kengai :bunjin])

(def styles
  {:chokkan {:name "CHOKKAN" :description "formal upright"
             :trunk-height-frac 0.65 :trunk-curve-amp 0.5
             :trunk-curve-waves 0.0 :trunk-lean 0.0
             :branch-density 0.45 :branch-length 6.5
             :max-depth 6 :foliage-size 3.0}
   :moyogi  {:name "MOYOGI" :description "informal upright"
             :trunk-height-frac 0.65 :trunk-curve-amp 4.0
             :trunk-curve-waves 1.7 :trunk-lean 0.0
             :branch-density 0.50 :branch-length 6.0
             :max-depth 6 :foliage-size 2.8}
   :shakan  {:name "SHAKAN" :description "slanting"
             :trunk-height-frac 0.62 :trunk-curve-amp 0.0
             :trunk-curve-waves 0.0 :trunk-lean 0.30
             :branch-density 0.55 :branch-length 5.5
             :max-depth 6 :foliage-size 2.7}
   :kengai  {:name "KENGAI" :description "cascade"
             :trunk-height-frac 0.50 :trunk-curve-amp 0.0
             :trunk-curve-waves 0.0 :trunk-lean 0.0
             :branch-density 0.55 :branch-length 6.5
             :max-depth 6 :foliage-size 2.5}
   :bunjin  {:name "BUNJIN" :description "literati"
             :trunk-height-frac 0.78 :trunk-curve-amp 1.0
             :trunk-curve-waves 0.5 :trunk-lean 0.05
             :branch-density 0.18 :branch-length 4.5
             :max-depth 5 :foliage-size 2.0}})

(def default-config
  {:grid {:width 96 :height 42}
   :tree {:cell-aspect 2.0
          :min-branch-length 1.5
          :branch-length-factor 0.66
          :min-trunk-height 4.0
          :trunk-segments-per-cell 1.8
          :trunk-segments-min 6
          :trunk-segments-max 30
          :branch-band [0.30 0.92]
          :kengai-apex-frac 0.30}
   :clock {:simulation-fps {:min 10 :initial 30 :max 120 :step 10}
           :render-fps 60
           :max-frame-delta-ms 100}
   :animation {:rustle-frequency 6.0
               :rustle-rate 0.06
               :growth-seconds 2.8
               :wind-min 0
               :wind-initial 1
               :wind-max 3}
   :render {:cell-width 11
            :cell-height 18
            :terminal-cell-aspect 2.0
            :hud-height 72
            :footer-height 34
            :font-size 17
            :background [5 7 11]
            :hud-background [13 17 24]
            :hud-text [225 229 235]}})

(defn grid-width [simulation-config]
  (get-in simulation-config [:grid :width]))

(defn grid-height [simulation-config]
  (get-in simulation-config [:grid :height]))

(defn cell-count [simulation-config]
  (* (grid-width simulation-config) (grid-height simulation-config)))

(defn style [style-key]
  (get styles style-key))

(defn style-name [style-key]
  (:name (style style-key)))

(defn- finite-number? [value]
  (and (number? value) (Double/isFinite (double value))))

(defn- positive-number? [value]
  (and (finite-number? value) (pos? value)))

(defn- non-negative-number? [value]
  (and (finite-number? value) (not (neg? value))))

(defn- unit-interval? [value]
  (and (finite-number? value) (<= 0.0 (double value) 1.0)))

(defn- open-unit-interval? [value]
  (and (finite-number? value) (< 0.0 (double value) 1.0)))

(defn- byte-value? [value]
  (and (int? value) (<= 0 value 255)))

(defn- rgb-color? [value]
  (and (vector? value)
       (= 3 (count value))
       (every? byte-value? value)))

(defn- check-value! [path value valid? message]
  (when-not (valid? value)
    (throw (ex-info (str "Invalid bonsai configuration at " path ": " message)
                    {:path path :value value}))))

(defn- validate-style! [style-key style-settings]
  (let [at #(vector :styles style-key %1)]
    (check-value! (at :name) (:name style-settings)
                  #(and (string? %) (not-empty %)) "must be a non-empty string")
    (check-value! (at :description) (:description style-settings)
                  #(and (string? %) (not-empty %)) "must be a non-empty string")
    (check-value! (at :trunk-height-frac) (:trunk-height-frac style-settings)
                  #(and (finite-number? %)
                        (< 0.0 (double %))
                        (<= (double %) 1.0))
                  "must be in (0, 1]")
    (doseq [key [:trunk-curve-amp :trunk-curve-waves]]
      (check-value! (at key) (get style-settings key) non-negative-number?
                    "must be a non-negative finite number"))
    (check-value! (at :trunk-lean) (:trunk-lean style-settings) finite-number?
                  "must be finite")
    (check-value! (at :branch-density) (:branch-density style-settings)
                  unit-interval? "must be in [0, 1]")
    (doseq [key [:branch-length :foliage-size]]
      (check-value! (at key) (get style-settings key) positive-number?
                    "must be a positive finite number"))
    (check-value! (at :max-depth) (:max-depth style-settings) pos-int?
                  "must be a positive integer")))

(defn validate!
  "Return config unchanged, or throw ex-info describing an invalid setting."
  [simulation-config]
  (let [checks [[[:grid :width] #(and (int? %) (<= 16 %))
                 "must be an integer of at least 16"]
                [[:grid :height] #(and (int? %) (<= 10 %))
                 "must be an integer of at least 10"]
                [[:tree :cell-aspect] positive-number?
                 "must be a positive finite number"]
                [[:tree :min-branch-length] positive-number?
                 "must be a positive finite number"]
                [[:tree :branch-length-factor] open-unit-interval?
                 "must be in (0, 1)"]
                [[:tree :min-trunk-height] positive-number?
                 "must be a positive finite number"]
                [[:tree :trunk-segments-per-cell] positive-number?
                 "must be a positive finite number"]
                [[:tree :trunk-segments-min] pos-int?
                 "must be a positive integer"]
                [[:tree :trunk-segments-max] pos-int?
                 "must be a positive integer"]
                [[:tree :kengai-apex-frac] open-unit-interval?
                 "must be in (0, 1)"]
                [[:clock :simulation-fps :min] pos-int?
                 "must be a positive integer"]
                [[:clock :simulation-fps :initial] pos-int?
                 "must be a positive integer"]
                [[:clock :simulation-fps :max] pos-int?
                 "must be a positive integer"]
                [[:clock :simulation-fps :step] pos-int?
                 "must be a positive integer"]
                [[:clock :render-fps] pos-int? "must be a positive integer"]
                [[:clock :max-frame-delta-ms] pos-int?
                 "must be a positive integer"]
                [[:animation :rustle-frequency] positive-number?
                 "must be a positive finite number"]
                [[:animation :rustle-rate] unit-interval?
                 "must be in [0, 1]"]
                [[:animation :growth-seconds] positive-number?
                 "must be a positive finite number"]
                [[:render :cell-width] pos-int? "must be a positive integer"]
                [[:render :cell-height] pos-int? "must be a positive integer"]
                [[:render :terminal-cell-aspect] positive-number?
                 "must be a positive finite number"]
                [[:render :hud-height] nat-int?
                 "must be a non-negative integer"]
                [[:render :footer-height] nat-int?
                 "must be a non-negative integer"]
                [[:render :font-size] pos-int? "must be a positive integer"]
                [[:render :background] rgb-color? "must be an RGB byte vector"]
                [[:render :hud-background] rgb-color?
                 "must be an RGB byte vector"]
                [[:render :hud-text] rgb-color? "must be an RGB byte vector"]]]
    (doseq [[path valid? message] checks]
      (check-value! path (get-in simulation-config path) valid? message))

    (let [[band-low band-high :as branch-band]
          (get-in simulation-config [:tree :branch-band])]
      (check-value! [:tree :branch-band] branch-band
                    #(and (vector? %) (= 2 (count %))
                          (finite-number? band-low)
                          (finite-number? band-high)
                          (<= 0.0 band-low)
                          (< band-low band-high)
                          (<= band-high 1.0))
                    "must be [low high] with 0 <= low < high <= 1"))

    (let [segment-min (get-in simulation-config [:tree :trunk-segments-min])
          segment-max (get-in simulation-config [:tree :trunk-segments-max])]
      (when-not (<= segment-min segment-max)
        (throw (ex-info "Minimum trunk segments must not exceed maximum"
                        {:path [:tree :trunk-segments]
                         :values [segment-min segment-max]}))))

    (let [minimum-height (get-in simulation-config [:tree :min-trunk-height])
          grid-height (grid-height simulation-config)]
      (when-not (< minimum-height grid-height)
        (throw (ex-info "Minimum trunk height must fit inside the grid"
                        {:path [:tree :min-trunk-height]
                         :value minimum-height
                         :grid-height grid-height}))))

    (let [{:keys [min initial max]} (get-in simulation-config
                                             [:clock :simulation-fps])]
      (when-not (<= min initial max)
        (throw (ex-info "Invalid simulation FPS range"
                        {:path [:clock :simulation-fps]
                         :values [min initial max]}))))
    (let [{:keys [wind-min wind-initial wind-max]}
          (:animation simulation-config)]
      (when-not (and (nat-int? wind-min)
                     (nat-int? wind-initial)
                     (nat-int? wind-max)
                     (<= wind-min wind-initial wind-max))
        (throw (ex-info "Invalid animation settings"
                        {:path [:animation]
                         :value (:animation simulation-config)}))))

    (check-value! [:style-order] style-order
                  #(and (vector? %)
                        (= (count %) (count (distinct %)))
                        (every? (set (keys styles)) %))
                  "must contain unique, known style keys")
    (doseq [style-key style-order]
      (validate-style! style-key (get styles style-key)))

    simulation-config))
