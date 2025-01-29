(ns io.factorhouse.rfx.demo
  (:require [io.factorhouse.rfx.core :as rfx]
            [io.factorhouse.rfx.dev :as dev]
            [io.factorhouse.rfx.stores.zustand :as zustand]
            [io.factorhouse.rfx.stores.atom :as atom]
            ["react" :as react]
            ["react-dom/client" :refer [createRoot]]))

(rfx/reg-event-db
  :counter/increment
  (fn [db _]
    (prn "Update?")
    (update db :number inc)))

(rfx/reg-sub
  :counter
  (fn [db _]
    (:number db)))

(rfx/reg-sub
  :persistence
  (fn [db _]
    (:persistence db)))

(defn p-view []
  (let [p (rfx/use-sub [:persistence])]
    (prn "Updated p?!")
    (react/createElement "div" #js {} (pr-str p))))

(defn counter-view []
  (let [counter (rfx/use-sub [:counter])]
    (react/createElement
      "div" #js {}
      (str "The value of the counter is " counter))))

(defn test-ui-react []
  (let []
    (react/createElement
      "div" #js {:onClick #(rfx/dispatch [:counter/increment])}
      (react/createElement counter-view)
      (react/createElement dev/error-log-view)
      (react/createElement dev/event-log-view)
      (react/createElement dev/subscription-view)
      (react/createElement p-view))))

(defonce root
  (createRoot (.getElementById js/document "app")))

(defonce app-db
  (atom/store {:number 0
               :persistence {:this-value :foo :should-never-change true}}))

(defn init []
  (rfx/init! (dev/opts {:store app-db}))
  (.render root (react/createElement test-ui-react))
  (prn "Hello world"))

(init)
