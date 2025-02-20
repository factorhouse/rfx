(ns rfx.components
  (:require [io.factorhouse.hsx.core :as hsx]
            [io.factorhouse.rfx.core :as rfx]))

(rfx/reg-sub
  :counter
  (fn [db _]
    (:counter db)))

(rfx/reg-event-db
  :counter/increment
  (fn [db _]
    (update db :counter inc)))

(defn wrap-rfx
  [initial-value comp]
  [:> rfx/RfxContextProvider #js {"value" (rfx/init {:initial-value initial-value})}
   comp])

(defn button []
  (let [value    (rfx/use-sub [:counter])
        dispatch (rfx/use-dispatch)]
    [:button {:on-click #(dispatch [:counter/increment])}
     (str "The value is " value)]))

(defn Button []
  (hsx/create-element
    [wrap-rfx {:counter 0} [button]]))
