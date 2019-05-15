(ns stylefy.impl.styles
  (:require [stylefy.impl.dom :as dom]
            [garden.core :refer [css]]
            [clojure.string :as str]
            [stylefy.impl.utils :as utils]
            [stylefy.impl.conversion :as conversion]
            [clojure.set :as set]
            [garden.compiler :as compiler]))

(def global-vendor-prefixes (atom {:stylefy.core/vendors #{}
                                   :stylefy.core/auto-prefix #{}}))
(def default-class-prefix "_stylefy")
(def use-custom-class-prefix? (atom false))

(defn- add-global-vendors [style]
  (merge style
         {:stylefy.core/vendors (set/union (:stylefy.core/vendors @global-vendor-prefixes)
                                           (:stylefy.core/vendors style))
          :stylefy.core/auto-prefix (set/union (:stylefy.core/auto-prefix @global-vendor-prefixes)
                                               (:stylefy.core/auto-prefix style))}))

(defn- check-custom-class-prefix
  "Checks that the value is valid and returns as properly formatted prefix."
  [custom-class-prefix]
  (assert (or
            (nil? custom-class-prefix)
            (string? custom-class-prefix)
            (keyword? custom-class-prefix))
          (str "Custom class prefix should be either string, keyword or nil, got: " (pr-str custom-class-prefix)))

  (cond (nil? custom-class-prefix) default-class-prefix
        (string? custom-class-prefix) custom-class-prefix
        (keyword? custom-class-prefix) (name custom-class-prefix)))

(defn hash-style [style]
  (when (not (empty? style))
    (let [hashable-garden-units (reduce
                                  ;; Convert Garden units to CSS to make them structurally
                                  ;; hashable (different contents = different hash)
                                  (fn [result prop-key]
                                    (let [prop-value (prop-key style)]
                                      (when (utils/is-garden-value? prop-value)
                                        (assoc result prop-key (compiler/render-css prop-value)))))
                                  {}
                                  (keys (utils/filter-css-props style)))
          hashable-style (merge style hashable-garden-units)
          ;; Hash style without certain special keywords:
          ;; - sub-styles is only a link to other styles, it does not define the actual properties of this style
          ;; - class-prefix is only for class naming, the style looks the same with it or without
          hashable-style (dissoc hashable-style
                                 :stylefy.core/sub-styles
                                 :stylefy.core/class-prefix)
          class-prefix (if @use-custom-class-prefix?
                         (check-custom-class-prefix (:stylefy.core/class-prefix style))
                         default-class-prefix)]
      (str class-prefix "_" (hash hashable-style)))))

(defn- create-style! [{:keys [props hash] :as style}]
  (dom/save-style! {:props props :hash hash})

  ;; Create sub-styles (if any)
  (doseq [sub-style (vals (:stylefy.core/sub-styles props))]
    (create-style! {:props sub-style :hash (hash-style sub-style)})))

(defn- prepare-style-return-value
  "Given a style, hash and options, returns HTML attributes for a Hiccup component,
   or nil if there are not any attributes."
  [style style-hash options]
  (let [with-classes (concat (:stylefy.core/with-classes style)
                             (:stylefy.core/with-classes options))
        html-attributes (utils/filter-css-props options)
        html-attributes-class (:class html-attributes)
        html-attributes-inline-style (:style html-attributes)
        final-class (str/trim
                      (cond
                        (nil? html-attributes-class)
                        (str/join " " (concat with-classes [style-hash]))

                        (string? html-attributes-class)
                        (str/join " " (concat [html-attributes-class] with-classes [style-hash]))

                        (vector? html-attributes-class)
                        (str/join " " (concat html-attributes-class with-classes [style-hash]))

                        :else nil))
        final-html-attributes (merge
                                html-attributes
                                (when (seq final-class)
                                  {:class final-class}))]

    (assert (or (nil? html-attributes-class)
                (string? html-attributes-class)
                (vector? html-attributes-class))
            (str "Unsupported :class type (should be nil, string or vector). Got: " (pr-str html-attributes-class)))
    (assert (nil? html-attributes-inline-style)
            "HTML attribute :style is not supported in options map. Instead, you should provide your style definitions as the first argument when calling use-style.")

    (when (not (empty? final-html-attributes))
      final-html-attributes)))

(defn- style-return-value [style style-hash options]
  (let [return-map (prepare-style-return-value style style-hash options)]
    (if (or (empty? style)
            (dom/style-in-dom? style-hash))
      return-map
      ;; The style definition has not been added into the DOM yet, so return the style props
      ;; as inline style. Inline style gets replaced soon as the style definition
      ;; is added into the DOM and the component re-renders itself.
      ;; However, if there are media queries, specific mode definitions etc., inline styling is probably
      ;; going to look wrong. Thus, hide the component completely until the DOM is ready.
      (let [contains-media-queries? (some? (:stylefy.core/media style))
            contains-feature-queries? (some? (:stylefy.core/supports style))
            contains-manual-mode? (some? (:stylefy.core/manual style))
            excluded-modes #{:hover}
            contains-modes-not-excluded? (not (empty?
                                                (filter (comp not excluded-modes)
                                                        (keys (:stylefy.core/mode style)))))
            inline-style (-> style
                             (utils/filter-css-props)
                             (conversion/garden-units->css))]
        (if (or contains-media-queries?
                contains-feature-queries?
                contains-manual-mode?
                contains-modes-not-excluded?)
          (merge return-map {:style (merge inline-style
                                           {:visibility "hidden"})})
          (merge return-map {:style inline-style}))))))

(defn use-style! [style options]
  (let [with-classes-options (:stylefy.core/with-classes options)
        with-classes-style (:stylefy.core/with-classes style)]

    (assert (or (nil? with-classes-options)
                (and (vector? with-classes-options)
                     (every? string? with-classes-options)))
            (str "with-classes argument inside options map must be a vector of strings, got: " (pr-str with-classes-options)))

    (assert (or (nil? with-classes-style)
                (and (vector? with-classes-style)
                     (every? string? with-classes-style)))
            (str "with-classes argument inside style map must be a vector of strings, got: " (pr-str with-classes-style)))

    (dom/check-stylefy-initialisation)

    (let [style-with-global-vendors (when-not (empty? style) (add-global-vendors style))
          style-hash (hash-style style-with-global-vendors)
          already-created (dom/style-by-hash style-hash)]

      (when (and (not (empty? style-with-global-vendors))
                 (some? style-hash)
                 (not already-created))
        (create-style! {:props style-with-global-vendors :hash style-hash}))

      (style-return-value style-with-global-vendors style-hash options))))

(defn use-sub-style! [style sub-style options]
  (let [resolved-sub-style (get (:stylefy.core/sub-styles style) sub-style)]
    (if resolved-sub-style
      (use-style! resolved-sub-style options)
      (.warn js/console (str "Sub-style " (pr-str sub-style) " not found in style: " (pr-str style))))))

(defn sub-style
  [style & sub-styles]
  (let [resolved-sub-style (reduce #(get-in %1 [:stylefy.core/sub-styles %2])
                                   style
                                   sub-styles)]

    (if resolved-sub-style
      resolved-sub-style
      (.warn js/console (str "Sub-style " (pr-str sub-styles) " not found in style: " (pr-str style))))))

(defn prepare-styles
  ([styles]
   (prepare-styles styles {:request-dom-update-after-done? true}))
  ([styles {:keys [request-dom-update-after-done?] :as options}]
   (let [styles (remove nil? styles)]

     (doseq [style styles]
       (use-style! style {})
       (when-let [sub-styles (vals (:stylefy.core/sub-styles style))]
         (prepare-styles sub-styles {:request-dom-update-after-done? false}))))

   (when request-dom-update-after-done?
     (dom/update-dom-if-requested))))

(defn init-global-vendor-prefixes [options]
  (let [global-vendor-prefixes-options (:global-vendor-prefixes options)]
    (reset! global-vendor-prefixes
            {:stylefy.core/vendors (:stylefy.core/vendors global-vendor-prefixes-options)
             :stylefy.core/auto-prefix (:stylefy.core/auto-prefix global-vendor-prefixes-options)})))

(defn init-custom-class-prefix [options]
  (reset! use-custom-class-prefix? (boolean (:use-custom-class-prefix? options))))
