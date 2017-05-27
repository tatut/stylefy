(ns stylefy.examples.main
  (:require [reagent.core :as r]
            [stylefy.examples.styles :as styles]
            [stylefy.examples.grid :as grid]
            [cljs.core.async :refer [<! timeout]]
            [stylefy.core :as stylefy :refer [use-style use-sub-style]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- button-style-by-type [type]
  (case type
    :primary styles/primary-button
    :secondary styles/secondary-button
    styles/generic-button))

(defn- button
  ([text] (button text #() nil))
  ([text action-fn type]
   [:div (merge (use-style (button-style-by-type type))
                {:on-click action-fn})
    text]))

(defn- button-container []
  [:div (use-style styles/generic-container)
   [button "Hello World!"]
   [button "Primary" #() :primary]
   [button "Secondary" #() :secondary]])

(defn stateful-component []
  (let [switch #(if (= :on %) :off :on)
        state (r/atom :on)]
    (fn []
      [:div (use-style (@state styles/stateful-component))
       (if (= @state :on)
         [:p "The component's current state is ON"]
         [:p "The component's current state is OFF"])
       [button "Switch" #(reset! state (switch @state)) :primary]])))

(defn- stress-test-item [index]
  (fn [style]
    [:div style index]))

(defn- create-random-style [index]
  {:padding "5px"
   :width (str (/ index 10) "%")
   :height "30px"
   :margin-bottom "5px"
   :background-color (str "#"
                          (rand-int 10)
                          (rand-int 10)
                          (rand-int 10)
                          (rand-int 10)
                          (rand-int 10)
                          (rand-int 10))})

(defn stress-test []
  (let [components-count 1000
        state (r/atom :hidden)
        styles (mapv create-random-style (range 0 components-count))]
    (fn []
      [:div (use-style styles/generic-container)

       [button
        (case @state
          :hidden "Generate"
          :generating "Generating..."
          :visible "Hide")
        #(case @state
           :hidden (go (reset! state :generating)
                       (<! (timeout 100))
                       (reset! state :visible))
           :visible (reset! state :hidden))
        :primary]

       (if (= @state :visible)
         (map-indexed (fn [index component]
                        ^{:key index}
                        [component (use-style (get styles index))])
                      (map stress-test-item (range 0 components-count))))])))

(defn- bs-navbar-item [index index-atom text]
  [:li (merge (use-style styles/clickable
                         (when (= @index-atom index)
                           {::stylefy/with-classes ["active"]}))
              {:role "presentation"
               :on-click #(reset! index-atom index)})
   [:a text]])

(defn- bs-navbar []
  (let [active-index (r/atom 0)]
    (fn []
      [:ul.nav.nav-pills (use-style styles/boostrap-navbar-overrides)
       [bs-navbar-item 0 active-index "One"]
       [bs-navbar-item 1 active-index "Two"]
       [bs-navbar-item 2 active-index "Three"]
       [bs-navbar-item 3 active-index "Four"]])))

(defn- responsive-layout []
  [:div (use-style styles/responsive-layout)
   [:div (use-sub-style styles/responsive-layout :column1)
    [:p "This is column 1"]
    [:p "This is column 1"]
    [:p "This is column 1"]
    [:p "This is column 1"]
    [:p "This is column 1"]]
   [:div (use-sub-style styles/responsive-layout :column2)
    [:p "This is column 2"]
    [:p "This is column 2"]
    [:p "This is column 2"]]
   [:div (use-sub-style styles/responsive-layout :column3)
    [:p "This is column 3"]
    [:p "This is column 3"]]])

(defn animation []
  [:div (use-style styles/animated-box)])

(defn- examples []
  (fn []
    ;; Some browsers (read IE11) require that animations are present in DOM before
    ;; they can be used correctly in components.
    (when @stylefy/keyframes-in-dom?
      [:div (use-style (merge styles/root
                              styles/general-styles))
       [:h1 "Generic button"]
       [:p "Just a simple styled button to begin with."]
       [button "Generic button"]

       [:h1 "Different type of buttons in a container"]
       [:p "Styled by merging styles"]
       [button-container]

       [:h1 "Component with multiple sub elements"]
       [:p "Styled by using sub-styles"]
       [grid/grid
        {:title "Example grid"}
        [{:title "Product" :name :name}
         {:title "ID" :name :id}
         {:title "Price (€/kg)" :name :price}]
        [{:name "Apple" :id 13 :price 1}
         {:name "Orange" :id 35 :price 2}
         {:name "Banana" :id 15 :price 3}]]

       [:h1 "Component with internal state"]
       [:p "This component contains a different style in different states. The styles are generated and inserted into DOM on-demand."]
       [stateful-component]

       [:h1 "Stress test"]
       [:p "Styles are added into DOM on-demand when they are used for the first time. Clicking the button below generates 1000 different looking components dynamically. The components are first styled with inline styles until the DOM has been updated and we can begin using CSS classes to save memory."]
       [stress-test]

       [:h1 "Boostrap navbar"]
       [:p "You can also assign any classes to elements normally. Here we use Boostrap classes to construct a simple navbar. We also override some BS styles."]
       [bs-navbar]

       [:h1 "Responsive layout"]
       [:p "stylefy supports media queries out of the box"]
       [responsive-layout]

       [:h1 "Animations"]
       [:p "stylefy also supports keyframes"]
       [animation]])))

(defn main []
  [examples])

(defn ^:export start []
  (stylefy/init)
  (r/render main (.getElementById js/document "app")))