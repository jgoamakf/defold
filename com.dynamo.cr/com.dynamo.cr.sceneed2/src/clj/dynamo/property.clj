(ns dynamo.property
  (:require [schema.core :as s]
            [dynamo.types :as t]
            [dynamo.ui :as ui]
            [dynamo.util :refer :all]
            [internal.property :as ip]))

(set! *warn-on-reflection* true)

(defmacro defproperty [name value-type & body-forms]
  (apply ip/def-property-type-descriptor name value-type body-forms))

(defproperty NonNegativeInt s/Int
  (validate positive? (comp not neg?)))

(defproperty Resource s/Str (tag ::resource))
(defproperty ImageResource Resource (tag ::image))

(defproperty ResourceList [s/Str] (tag ::resource) (default []))
(defproperty ImageResourceList ResourceList (tag ::image))

(defproperty Vec3 t/Vec3
  (default [0.0 0.0 0.0]))

(defproperty Color Vec3
  (tag :color))

(defprotocol Presenter
  (control-for-property [this])
  (settings-for-control [this value])
  (on-event [this widget-subtree path event value]))

(defn no-change [] nil)
(defn intermediate-value [v] {:update-type :intermediate :value v})
(defn final-value [v]        {:update-type :final :value v})
(defn reset-default []       {:update-type :reset})

(defn presenter-event-map
  "Translate event map (from `dynamo.ui/event->map`) to stable external event map exposed to property presenters."
  [event-map]
  (let [whitelist #{:type :data}
        qualified #{:character}]
    (merge
      (select-keys event-map whitelist)
      (map-keys #(keyword (namespace ::_) (name %)) (select-keys event-map qualified)))))

(defn is-enter-key? [event]
  (#{\return \newline} (::character event)))

(defrecord DefaultPresenter []
  Presenter
  (control-for-property [_]
    {:type :label})
  (settings-for-control [_ value]
    {:text (str value)}))

(def default-presenter
  (->DefaultPresenter))

(s/defn register-presenter :- t/Registry
  [registry   :- t/Registry
   type       :- {:value-type s/Any (s/optional-key :tag) s/Keyword}
   presenter  :- (s/protocol Presenter)]
  (assoc registry (merge {:tag nil} type) presenter))

(s/defn lookup-presenter :- (s/protocol Presenter)
  [registry :- t/Registry
   property :- (s/protocol t/PropertyType)]
  (loop [tags (conj (t/property-tags property) nil)]
    (if (empty? tags)
      default-presenter
      (let [key {:value-type (t/property-value-type property) :tag (first tags)}]
        (if (contains? registry key)
          (get registry key)
          (recur (rest tags)))))))
