(ns io.factorhouse.rfx.registry)

(defn reg-cofx
  [registry cofx-id cofx-fn]
  (swap! registry assoc-in [:cofx cofx-id] cofx-fn))

(defn reg-sub
  [registry sub-id signals sub-f]
  (let [sub {:sub-f sub-f :signals signals}]
    (swap! registry assoc-in [:sub sub-id] sub)))

(defn reg-fx
  [registry fx-id f]
  (swap! registry assoc-in [:fx fx-id] f))

(defn reg-event-fx
  [registry event-fx-id interceptors f]
  (let [fx {:event-f      f
            :interceptors interceptors}]
    (swap! registry assoc-in [:event event-fx-id] fx)))

(defn reg-event-db
  [registry event-id interceptors event-f]
  (let [event {:event-f      (fn [{:keys [db]} event] {:db (event-f db event)})
               :interceptors interceptors}]
    (swap! registry assoc-in [:event event-id] event)))

(defn clear-subscription-cache!
  [registry]
  (swap! registry dissoc :sub))
