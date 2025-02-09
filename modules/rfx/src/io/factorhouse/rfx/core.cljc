(ns io.factorhouse.rfx.core
  "An implementation of re-frame built for modern React"
  (:require [io.factorhouse.rfx.loggers :as loggers]
            [io.factorhouse.rfx.queue :as queue]
            [io.factorhouse.rfx.stores.atom :as stores.atom]
            [io.factorhouse.rfx.store :as store]
            [io.factorhouse.rfx.queues.stable :as stable-queue]
            [io.factorhouse.rfx.registry :as registry]
            #?(:cljs ["react" :as react])))

(defonce global-registry
  (atom {}))

(defn log-and-continue-error-handler
  [errors]
  (prn "Errors => " (pr-str errors)))

(defn dispatch
  [{:keys [queue]} event]
  (if (nil? event)
    (throw (ex-info "re-frame: you called \"dispatch\" without an event vector." {}))
    (queue/push queue event)))

(defn reg-cofx
  [cofx-id cofx-fn]
  (registry/reg-cofx global-registry cofx-id cofx-fn))

(defn inject-cofx
  ([id]
   {:id id :value nil})
  ([id value]
   {:id id :value value}))

(defn handler
  [registry store error-handler]
  (fn [event-queue [event-id & _args :as event]]
    (let [curr-registry @registry
          errors        (atom [])]
      (if-let [{:keys [event-f coeffects]} (get-in curr-registry [:event event-id])]
        (let [curr-state (store/snapshot-state store)
              ctx        (reduce
                           (fn [ctx {:keys [id value]}]
                             (if-let [cofx-fn (get-in curr-registry [:cofx id])]
                               (cofx-fn ctx value)
                               (do
                                 (swap! errors conj {:type    :missing-cofx
                                                     :level   :warn
                                                     :message (str "No such cofx named " (pr-str id) ". Returning previous coeffects.")})
                                 ctx)))
                           {:db     curr-state
                            ::store store}
                           coeffects)
              result     (event-f ctx event)]

          (when-let [next-db (:db result)]
            (store/snapshot-reset! store next-db))

          (when-let [dispatch-event (:dispatch result)]
            (queue/push event-queue dispatch-event))

          (doseq [dispatch-event (:dispatch-n result)]
            (queue/push event-queue dispatch-event))

          (doseq [[fx-id fx-val] (dissoc result :db :dispatch :dispatch-n)]
            (if-let [fx-fn (get-in curr-registry [:fx fx-id])]
              (fx-fn fx-val)
              (swap! errors conj {:type    :missing-fx
                                  :level   :warn
                                  :message (str "Cannot find fx named " (pr-str fx-id))}))))

        (swap! errors conj {:type    :missing-event
                            :level   :warn
                            :message (str "Cannot find event named " (pr-str event-id) ".")}))

      (when-let [errors (seq @errors)]
        (error-handler {:errors errors
                        :origin event})))))

(defn dispatch-sync
  [{:keys [store error-handler queue]} event]
  (let [handler-f (handler global-registry store error-handler)]
    (handler-f queue event)))

(defn reg-sub
  ([sub-id]
   (reg-sub sub-id [] (fn [db _] db)))
  ([sub-id sub-f]
   (reg-sub sub-id [] sub-f))
  ([sub-id signals sub-f]
   (registry/reg-sub global-registry sub-id signals sub-f)))

(defn cofx-subscribe
  [coeffects [sub-id & _sub-args :as sub]]
  (assoc coeffects sub-id (store/subscribe (::store coeffects) sub)))

(reg-cofx ::subscribe cofx-subscribe)

(defn reg-fx
  [fx-id f]
  (registry/reg-fx global-registry fx-id f))

(defn reg-event-fx
  ([event-fx-id f]
   (reg-event-fx event-fx-id nil f))
  ([event-fx-id coeffects f]
   (registry/reg-event-fx global-registry event-fx-id coeffects f)))

(defn reg-event-db
  [event-id event-f]
  (registry/reg-event-db global-registry event-id event-f))

(defn clear-subscription-cache!
  []
  (registry/clear-subscription-cache! global-registry))

(defn snapshot-sub
  [{:keys [store]} sub]
  (store/subscribe store sub))

(defn snapshot-state
  [{:keys [store]}]
  (store/snapshot-state store))

(defn init
  [{:keys [initial-value queue error-handler store registry]
    :or   {initial-value {}
           registry      global-registry
           error-handler log-and-continue-error-handler
           queue         stable-queue/event-queue
           store         stores.atom/store}}]
  (let [store*         (store registry initial-value)
        handler*       (handler registry store* error-handler)
        queue*         (queue handler* error-handler)
        use-sub*       (fn use-sub* [sub]
                         (store/use-sub store* sub))
        dispatch-sync* (fn dispatch-sync* [event]
                         (handler* queue* event))
        ctx            {:store         store*
                        :error-handler error-handler
                        :handler       handler*
                        :queue         queue*
                        :use-sub       use-sub*
                        :registry      registry
                        :dispatch-sync dispatch-sync*}
        dispatch*      (fn dispatch* [event]
                         (dispatch ctx event))]
    (assoc ctx :dispatch dispatch*)))

(defonce global-context
  (init {}))

#?(:cljs
   (defonce RfxContext
     (react/createContext global-context)))

#?(:cljs
   (defonce RfxContextProvider
     (.-Provider RfxContext)))

#?(:cljs
   (defn use-rfx-context []
     (react/useContext RfxContext)))

#?(:cljs
   (defn use-sub
     [sub]
     (let [ctx       (use-rfx-context)
           use-sub-f (:use-sub ctx)]
       (use-sub-f sub))))

#?(:cljs
   (defn use-dispatch []
     (let [ctx (use-rfx-context)]
       (:dispatch ctx))))

#?(:cljs
   (defn use-dispatch-sync []
     (let [ctx (use-rfx-context)]
       (:dispatch-sync ctx))))
