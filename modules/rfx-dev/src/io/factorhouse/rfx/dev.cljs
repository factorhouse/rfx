(ns io.factorhouse.rfx.dev
  (:require [io.factorhouse.rfx.core :as rfx]
            [io.factorhouse.rfx.queue :as queue]
            [io.factorhouse.rfx.queues.stable :as queues.stable]
            [io.factorhouse.rfx.store :as store]
            [io.factorhouse.rfx.stores.zustand :as zustand]
            ["react" :as react]
            ["react-dom/client" :refer [createRoot]]))

(defonce event-log
  (zustand/store []))

(defonce error-log
  (zustand/store []))

(defonce sub-log
  (zustand/store {}))

(defn now []
  (js/Date.now))

(defn mark-event!
  [event]
  (store/snapshot-reset! event-log (conj @event-log (assoc event :ts (now)))))

(defn mark-sub!
  [id sub f]
  (store/snapshot-reset!
    sub-log
    (update-in @sub-log [sub :watchers]
               (fn [watchers]
                 (assoc watchers id {:sub-f f :ts (now)})))))

(defn unmark-sub!
  [id sub]
  (store/snapshot-reset!
    sub-log
    (update-in @sub-log [sub :watchers]
               (fn [watchers]
                 (dissoc watchers id)))))

(defn event-log-view []
  (let [count (store/use-store event-log identity)]
    (react/createElement "div" #js {} "Event log: " (pr-str count))))

(defn error-log-view []
  (let [count (store/use-store error-log count)]
    (react/createElement "div" #js {} "Error log: " (pr-str count))))

(defn subscription-view []
  (let [count (store/use-store sub-log identity)]
    (react/createElement "div" #js {} "Sub log: " (pr-str count))))

(defn event-queue
  [handler error-handler]
  (let [queue* (queues.stable/event-queue handler error-handler)]
    (reify queue/IEventQueue
      (push
        [_ event]
        (mark-event! {:type :push :event event})
        (queue/push queue* event))

      (add-post-event-callback
        [_ id callback-fn]
        (mark-event! {:type        :add-post-event-callback
                      :id          id
                      :callback-fn callback-fn})
        (queue/add-post-event-callback queue* id callback-fn))

      (remove-post-event-callback
        [_ id]
        (mark-event! {:type :remove-post-event-callback
                      :id   id})
        (queue/remove-post-event-callback queue* id))

      (purge [_]
        (mark-event! {:type :purge})
        (queue/purge queue*))

      (-fsm-trigger [_ trigger arg]
        (mark-event! {:type :fsm-trigger :trigger trigger :arg arg})
        (queue/-fsm-trigger queue* trigger arg))

      (-add-event [_ event]
        (mark-event! {:type :add-event :event event})
        (queue/-add-event queue* event))

      (-process-1st-event-in-queue [_]
        (mark-event! {:type :process-1st-event-in-queue})
        (queue/-process-1st-event-in-queue queue*))

      (-run-next-tick [_]
        (mark-event! {:type :run-next-tick})
        (queue/-run-next-tick queue*))

      (-run-queue [_]
        (mark-event! {:type :run-queue})
        (queue/-run-queue queue*))

      (-exception [_ ex]
        (mark-event! {:type :exception})
        (queue/-exception queue* ex))

      (-pause [_ later-fn]
        (mark-event! {:type :pause :later-fn later-fn})
        (queue/-pause queue* later-fn))

      (-resume [_]
        (mark-event! {:type :resume})
        (queue/-resume queue*))

      (-call-post-event-callbacks [_ event]
        (mark-event! {:type :call-post-event-callbacks :event event})
        (queue/-call-post-event-callbacks queue* event)))))

(defn error-handler
  [{:keys [errors] :as error}]
  (rfx/log-and-continue-error-handler error)
  (store/snapshot-reset! error-log (into @event-log (map #(assoc % :ts (now)) errors))))

(defn trace-store
  [store]
  (reify store/IStore
    (use-store [_ f]
      (let [[id _] (react/useState (str (gensym "sub")))]

        (react/useEffect
          (fn []
            (let [{:keys [sub]} (meta f)]
              (mark-sub! id sub f)
              (fn []
                (unmark-sub! id sub))))
          #js [])

        (store/use-store store f)))
    (snapshot-state [_]
      (store/snapshot-state store))
    (snapshot-reset! [_ newval]
      (store/snapshot-reset! store newval))))

(defn opts
  [{:keys [store]}]
  {:queue         event-queue
   :error-handler error-handler
   :store         (trace-store store)})
