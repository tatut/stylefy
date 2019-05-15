(ns stylefy.impl.utils
  (:require [garden.core :refer [css]]
            [garden.color :as color]
            [garden.types :as types]
            [garden.stylesheet :refer [at-media at-keyframes at-font-face]]
            [clojure.string :as str]))

(defn filter-css-props
  "Removes stylefy's namespaced keywords from the given map."
  [props]
  (apply dissoc props (filter #(and (namespace %)
                                    (str/starts-with? (namespace %) "stylefy"))
                              (keys props))))

(defn is-garden-value? [value]
  ; Note: types/CSSAtRule is not included since it is a selector, not a valid CSS value.
  (or (instance? #?(:cljs types/CSSUnit :clj garden.types.CSSUnit) value)
      (instance? #?(:cljs color/CSSColor :clj garden.color.CSSColor) value)
      (instance? #?(:cljs types/CSSFunction :clj garden.types.CSSFunction) value)))

(defn warn [& msg]
  (let [m (str/join " " msg)]
    #?(:cljs (.warn js/console m)
       :clj (println m))))
