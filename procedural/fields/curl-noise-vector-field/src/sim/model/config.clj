;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns sim.model.config
  "Own adjustable vector-field settings and staged validation.

  WHY: field sampling, particles, views, clocks, projection, and renderers need
  one trusted settings boundary. DATA: grid, field, particle, clock, and render
  rules. OPERATIONS: pattern/dimension queries and named validation stages.
  BOUNDARY: live world values and derived samples do not belong here.")

(def pattern-order
  [:particles :vector :potential :curl-magnitude :warped])

(def particle-patterns #{:particles :warped})
(def static-patterns #{:vector :potential :curl-magnitude})
(def supported-patterns (into particle-patterns static-patterns))
(def supported-band-count 4)

(defn particle-pattern? [pattern]
  (contains? particle-patterns pattern))

(defn static-pattern? [pattern]
  (contains? static-patterns pattern))

(defn supported-pattern? [pattern]
  (contains? supported-patterns pattern))

(def default-config
  {:grid
   {:width 120
    :height 68}

   :particles
   {:count 256
    :lifetime-seconds {:min 1.0
                       :max 6.0}
    :speed {:min 1
            :initial 8
            :max 64}}

   :clock
   {:simulation-fps {:min 10
                     :initial 60
                     :max 240
                     :step 10}
    :render-fps 60
    :max-frame-delta-ms 100}

   :field
   {:noise-scale 0.05
    :drift-rate 0.10
    :drift-multiplier {:min 1
                       :initial 1
                       :max 32}}

   :noise
   {:fbm-octaves 3}

   :curl
   {:epsilon 0.5
    :velocity-epsilon 1.0e-6}

   :warp
   {:amount 3.0
    :offset [5.2 1.3]}

   :trail
   {:decay 0.6
    :hit-intensity 1.0}

   :flash
   {:initial 1.0
    :decay 4.0
    :sparkle-mask 3}

   :render
   {:glow-threshold 0.05
    :terminal-cell-aspect 2.0
    :band-count supported-band-count
    :density-thresholds {:mid 0.30
                         :high 0.65}
    :potential {:midpoint 0.5
                :range 0.5}}

   :views
   {:order pattern-order
    :vector {:lattice [4 2]
             :stationary-threshold 0.05
             :axis-dominance 2.0}
    :curl-magnitude {:gain 1.5}}})

(defn grid-width [config]
  (get-in config [:grid :width]))

(defn grid-height [config]
  (get-in config [:grid :height]))

(defn- finite-number? [value]
  (and (number? value)
       (Double/isFinite (double value))))

(defn- positive-number? [value]
  (and (finite-number? value) (pos? value)))

(defn- non-negative-number? [value]
  (and (finite-number? value) (not (neg? value))))

(defn- check-value! [config path valid? message]
  (let [value (get-in config path)]
    (when-not (valid? value)
      (throw (ex-info (str "Invalid simulation configuration at " path
                           ": " message)
                      {:path path :value value}))))
  config)

(defn- validate-values! [config]
  (doseq [[path valid? message]
          [[[:grid :width] pos-int? "must be a positive integer"]
           [[:grid :height] pos-int? "must be a positive integer"]
           [[:particles :count] pos-int? "must be a positive integer"]
           [[:particles :lifetime-seconds :min] positive-number?
            "must be a positive finite number"]
           [[:particles :lifetime-seconds :max] positive-number?
            "must be a positive finite number"]
           [[:particles :speed :min] pos-int? "must be a positive integer"]
           [[:particles :speed :initial] pos-int? "must be a positive integer"]
           [[:particles :speed :max] pos-int? "must be a positive integer"]
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
           [[:field :drift-multiplier :min] pos-int?
            "must be a positive integer"]
           [[:field :drift-multiplier :initial] pos-int?
            "must be a positive integer"]
           [[:field :drift-multiplier :max] pos-int?
            "must be a positive integer"]
           [[:field :noise-scale] positive-number?
            "must be a positive finite number"]
           [[:field :drift-rate] non-negative-number?
            "must be a non-negative finite number"]
           [[:noise :fbm-octaves] pos-int? "must be a positive integer"]
           [[:curl :epsilon] positive-number?
            "must be a positive finite number"]
           [[:curl :velocity-epsilon] positive-number?
            "must be a positive finite number"]
           [[:warp :amount] non-negative-number?
            "must be a non-negative finite number"]
           [[:trail :decay] non-negative-number?
            "must be a non-negative finite number"]
           [[:trail :hit-intensity] non-negative-number?
            "must be a non-negative finite number"]
           [[:flash :initial] non-negative-number?
            "must be a non-negative finite number"]
           [[:flash :decay] non-negative-number?
            "must be a non-negative finite number"]
           [[:flash :sparkle-mask] nat-int?
            "must be a non-negative integer"]
           [[:render :glow-threshold] non-negative-number?
            "must be a non-negative finite number"]
           [[:render :terminal-cell-aspect] positive-number?
            "must be a positive finite number"]
           [[:render :band-count] pos-int? "must be a positive integer"]
           [[:render :density-thresholds :mid] non-negative-number?
            "must be a non-negative finite number"]
           [[:render :density-thresholds :high] non-negative-number?
            "must be a non-negative finite number"]
           [[:render :potential :midpoint] finite-number?
            "must be a finite number"]
           [[:render :potential :range] non-negative-number?
            "must be a non-negative finite number"]
           [[:views :vector :stationary-threshold] non-negative-number?
            "must be a non-negative finite number"]
           [[:views :vector :axis-dominance] positive-number?
            "must be a positive finite number"]
           [[:views :curl-magnitude :gain] positive-number?
            "must be a positive finite number"]]]
    (check-value! config path valid? message))
  config)

(defn- validate-ranges! [config]
  (doseq [path [[:particles :speed]
                [:clock :simulation-fps]
                [:field :drift-multiplier]]
          :let [{:keys [min initial max]} (get-in config path)]]
    (when-not (<= min initial max)
      (throw (ex-info (str "Invalid simulation configuration range at " path)
                      {:path path :values [min initial max]}))))
  (let [{:keys [min max]} (get-in config [:particles :lifetime-seconds])]
    (when-not (< min max)
      (throw (ex-info "Particle maximum lifetime must exceed its minimum"
                      {:path [:particles :lifetime-seconds]
                       :values [min max]}))))
  config)

(defn- validate-view-settings! [config]
  (let [lattice (get-in config [:views :vector :lattice])]
    (when-not (and (vector? lattice)
                   (= 2 (count lattice))
                   (every? pos-int? lattice))
      (throw (ex-info "Vector lattice dimensions must be positive integers"
                      {:path [:views :vector :lattice]
                       :value lattice}))))
  (let [offset (get-in config [:warp :offset])]
    (when-not (and (vector? offset)
                   (= 2 (count offset))
                   (every? finite-number? offset))
      (throw (ex-info "Warp offset must contain two finite numbers"
                      {:path [:warp :offset]
                       :value offset}))))
  (let [order (get-in config [:views :order])]
    (when-not (and (vector? order)
                   (seq order)
                   (= (count order) (count (distinct order)))
                   (every? supported-patterns order)
                   (some #{:particles} order))
      (throw (ex-info (str "View order must contain unique supported patterns "
                           "and include :particles")
                      {:path [:views :order]
                       :value order}))))
  config)

(defn- validate-render-settings! [config]
  (let [low (get-in config [:render :glow-threshold])
        mid (get-in config [:render :density-thresholds :mid])
        high (get-in config [:render :density-thresholds :high])]
    (when-not (< low mid high 1.0)
      (throw (ex-info "Render thresholds must satisfy glow < mid < high < 1"
                      {:path [:render :density-thresholds]
                       :values [low mid high]}))))
  (when-not (= supported-band-count (get-in config [:render :band-count]))
    (throw (ex-info (str "Themes require exactly " supported-band-count
                         " color bands")
                    {:path [:render :band-count]
                     :value (get-in config [:render :band-count])})))
  config)

(defn validate!
  "Return config unchanged, or throw with the path of an invalid setting."
  [config]
  (-> config
      validate-values!
      validate-ranges!
      validate-view-settings!
      validate-render-settings!))

(defn pattern-name [pattern]
  (case pattern
    :particles "PARTICLES"
    :vector "VECTOR"
    :potential "POTENTIAL"
    :curl-magnitude "CURL MAG"
    :warped "WARPED"
    "?"))

