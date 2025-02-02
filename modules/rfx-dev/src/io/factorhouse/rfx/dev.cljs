(ns io.factorhouse.rfx.dev
  (:require [io.factorhouse.rfx.core :as rfx]
            [io.factorhouse.rfx.queue :as queue]
            [io.factorhouse.rfx.store :as store]
            ["react" :as react]
            ["react-dom/client" :refer [createRoot]]))

(def initial-state
  {:event-log []
   :error-log []
   :sub-log   {}
   :epoch     0})

(defonce dev-context
  (rfx/init {:initial-state initial-state}))

(defn now []
  (js/Date.now))

(rfx/reg-event-db
  ::_epoch/increment
  (fn [db _]
    (update db :epoch inc)))

(rfx/reg-event-db
  ::_mark-event
  (fn [db [_ event]]
    (update db :event-log conj event)))

(rfx/reg-event-db
  ::_mark-sub
  (fn [db [_ id sub ts]]
    (update-in db [:sub-log sub :watchers]
               (fn [watchers]
                 (assoc watchers id {:ts ts})))))

(rfx/reg-event-db
  ::_unmark-sub
  (fn [db [_ id sub]]
    (update-in db [:sub-log sub :watchers]
               (fn [watchers]
                 (dissoc watchers id)))))

(rfx/reg-sub
  ::_event-log
  (fn [db _]
    (:event-log db)))

(rfx/reg-sub
  ::_error-log
  (fn [db _]
    (:error-log db)))

(rfx/reg-sub
  ::_sub-log
  (fn [db _]
    (:sub-log db)))

(defn event-log-view []
  (let [event-log (rfx/use-sub [::_event-log])]
    (react/createElement "div" #js {} "Event log: " (pr-str (count event-log)))))

(defn error-log-view []
  (let [error-log (rfx/use-sub [::_error-log])]
    (react/createElement "div" #js {} "Error log: " (pr-str (count error-log)))))

(defn subscription-view []
  (let [sub-log (rfx/use-sub [::_sub-log])]
    (react/createElement "div" #js {} "Sub log: " (pr-str (count sub-log)))))

(defn trace-queue
  [queue dev-dispatch]
  (reify queue/IEventQueue
    (push
      [_ event]
      (dev-dispatch [::_mark-event {:type  :push
                                    :event event
                                    :ts    (now)}])
      (queue/push queue event))

    (add-post-event-callback
      [_ id callback-fn]
      (dev-dispatch [::_mark-event {:type        :add-post-event-callback
                                    :id          id
                                    :ts          (now)
                                    :callback-fn callback-fn}])
      (queue/add-post-event-callback queue id callback-fn))

    (remove-post-event-callback
      [_ id]
      (dev-dispatch [::_mark-event {:type :remove-post-event-callback
                                    :id   id
                                    :ts   (now)}])
      (queue/remove-post-event-callback queue id))

    (purge [_]
      (dev-dispatch [::_mark-event {:type :purge
                                    :ts   (now)}])
      (queue/purge queue))))

(defn trace-error-handler
  (fn [app-error-handler _dispatch]
    (fn [e]
      (rfx/log-and-continue-error-handler e)
      (app-error-handler e))))

(defn trace-store
  [store dev-dispatch]
  (reify store/IStore
    (use-sub [_ sub]
      (let [[id _] (react/useState (str (gensym "sub")))]

        (react/useEffect
          (fn []
            (dev-dispatch [::_mark-sub id sub (now)])
            (fn []
              (dev-dispatch [::_unmark-sub id sub])))
          #js [])

        (store/use-sub store sub)))

    (snapshot-state [_]
      (store/snapshot-state store))

    (snapshot-reset! [_ newval]
      (dev-dispatch [::_epoch/increment])
      (store/snapshot-reset! store newval))))

(defn wrap-dev
  [app-context]
  (let [dispatch (:dispatch dev-context)]
    (-> app-context
        (update :store trace-store dispatch)
        (update :queue trace-queue dispatch)
        (update :error-handler trace-error-handler dispatch))))
