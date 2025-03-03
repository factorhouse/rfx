(ns datascript.ui
  (:require [datascript.core :as d]
            [datascript.rfx-store :as ds-rfx-store]
            [io.factorhouse.hsx.core :as hsx]
            [io.factorhouse.rfx.core :as rfx]
            ["react" :as react]
            ["react-dom/client" :refer [createRoot]]))

(def schema
  {:aka {:db/cardinality :db.cardinality/many}})

(defonce ctx
  (ds-rfx-store/init schema))

(defonce root
  (createRoot (.getElementById js/document "app")))

(rfx/reg-event-db
 :db/init!
 (constantly
  [{:db/id -1
    :name  "Maksim"
    :age   45
    :aka   ["Max Otto von Stierlitz", "Jack Ryan"]}]))

(rfx/reg-sub
 :db/aka
 (fn [db [_ name]]
   (d/q
    '[:find ?n ?a
      :in $ ?name
      :where [?e :aka ?name]
      [?e :name ?n]
      [?e :age ?a]]
    db name)))

(defn aka-view []
  (let [person   (rfx/use-sub [:db/aka "Max Otto von Stierlitz"])
        dispatch (rfx/use-dispatch)]

    (react/useEffect
     (fn []
       (dispatch [:db/init!])
       (constantly nil))
     #js [])

    (if person
      [:div "Found person named " person "!"]
      [:div "There is no person named \"Max Otto von Stierlitz\" in database :("])))

(defn ui []
  [:> rfx/RfxContextProvider #js {"value" ctx}
   [aka-view]])

(defn init []
  (.render
   root
   (hsx/create-element [ui])))

(init)
