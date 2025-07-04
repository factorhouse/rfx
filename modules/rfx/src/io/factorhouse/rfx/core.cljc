(ns io.factorhouse.rfx.core
  "An implementation of re-frame built for modern React"
  (:require [io.factorhouse.rfx.queue :as queue]
            [io.factorhouse.rfx.stores.atom :as stores.atom]
            [io.factorhouse.rfx.store :as store]
            [io.factorhouse.rfx.queues.stable :as stable-queue]
            [io.factorhouse.rfx.registry :as registry]
            #?(:cljs ["react" :as react])))

(defonce global-registry
  (atom {}))

(defn log-and-continue-error-handler
  [ctx]
  #?(:cljs (js/console.error "rfx:" (pr-str ctx))
     :clj  (let [log (requiring-resolve 'clojure.tools.logging/error)]
             (log "rfx: %s" ctx))))

(defn dispatch
  [{:keys [queue]} event]
  (queue/push queue event))

(defn reg-cofx
  [cofx-id cofx-fn]
  (registry/reg-cofx global-registry cofx-id cofx-fn))

(defn ->interceptor
  [& {:keys [id comment before after]}]
  (cond-> {:id     (or id :unnamed)
           :before before
           :after  after}
    comment (assoc :comment comment)))

(defn inject-cofx
  ([id]
   (->interceptor
    :id :coeffects
    :before (fn coeffects-before
              [context]
              (if-let [handler (get-in context [:coeffects ::registry :cofx id])]
                (update context :coeffects handler)
                (update context ::errors (fnil conj []) {:type    :missing-cofx
                                                         :level   :warn
                                                         :message (str "No such cofx named " (pr-str id) ". Returning previous coeffects.")})))))
  ([id value]
   (->interceptor
    :id :coeffects
    :before (fn coeffects-before
              [context]
              (if-let [handler (get-in context [:coeffects ::registry :cofx id])]
                (update context :coeffects handler value)
                (update context ::errors (fnil conj []) {:type    :missing-cofx
                                                         :level   :warn
                                                         :message (str "No such cofx named " (pr-str id) ". Returning previous coeffects.")}))))))

(defn handler
  [registry store error-handler]
  (let [ctx {:registry registry :store store :error-handler error-handler}]
    (fn handler* [event-queue [event-id & _args :as event]]
      (let [curr-registry @registry
            errors        (atom [])]
        (if-let [{:keys [event-f interceptors]} (get-in curr-registry [:event event-id])]
          (let [curr-state (store/snapshot store)
                ctx-before (loop [ctx {:coeffects {:db        curr-state
                                                   :event     event
                                                   ::registry curr-registry
                                                   ::store    store}
                                       :queue     interceptors
                                       :stack     []}]
                             (if-let [{:keys [before] :as interceptor} (first (:queue ctx))]
                               (recur (-> (if before (before ctx) ctx)
                                          (update :queue rest)
                                          (update :stack conj interceptor)))
                               ctx))
                effects    (event-f (:coeffects ctx-before) (-> ctx-before :coeffects :event))]
            (when-let [ctx-errors (::errors ctx-before)]
              (swap! errors into ctx-errors))

            (when-let [next-db (:db effects)]
              (store/next-state! store next-db))

            (when-let [dispatch-event (:dispatch effects)]
              (queue/push event-queue dispatch-event))

            (doseq [dispatch-event (:dispatch-n effects)]
              (queue/push event-queue dispatch-event))

            (doseq [[fx-id fx-val] (dissoc effects :db :dispatch :dispatch-n)
                    :let [ctx (assoc ctx :queue event-queue
                                     :dispatch (fn [event] (queue/push event-queue event))
                                     :dispatch-sync (fn [event] (handler* event-queue event)))]]
              (if-let [fx-fn (get-in curr-registry [:fx fx-id])]
                (fx-fn ctx fx-val)
                (swap! errors conj {:type    :missing-fx
                                    :level   :warn
                                    :message (str "Cannot find fx named " (pr-str fx-id))})))

            (when (seq interceptors)
              (let [ctx-after (loop [ctx {:coeffects (:coeffects ctx-before)
                                          :effects   effects
                                          :queue     (reverse interceptors)
                                          :stack     []}]
                                (if-let [{:keys [after] :as interceptor} (first (:queue ctx))]
                                  (recur (-> (if after (after ctx) ctx)
                                             (update :queue rest)
                                             (update :stack conj interceptor)))
                                  ctx))]
                (when-let [ctx-errors (::errors ctx-after)]
                  (swap! errors into ctx-errors)))))

          (swap! errors conj {:type    :missing-event
                              :level   :warn
                              :message (str "Cannot find event named " (pr-str event-id) ".")}))

        (when-let [errors (seq @errors)]
          (error-handler {:errors errors
                          :origin event}))))))

(defn dispatch-sync
  [{:keys [store error-handler queue registry]} event]
  (let [handler-f (handler registry store error-handler)]
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

(reg-fx
 :fx
 (fn [{:keys [dispatch registry] :as context} seq-of-effects]
   (let [curr-registry @registry]
     (if-not (sequential? seq-of-effects)
       (throw (ex-info (str "\":fx\" effect expects a seq, but was given " (type seq-of-effects))
                       {:input seq-of-effects}))
       (doseq [[effect-key effect-value] (remove nil? seq-of-effects)]
         (if (= :dispatch effect-key)
           (dispatch effect-value)
           (if-let [effect-fn (get-in curr-registry [:fx effect-key])]
             (effect-fn context effect-value)
             (throw (ex-info (str effect-key "in \":fx\" has no associated handler.")
                             {:input seq-of-effects})))))))))

(defn reg-event-fx
  ([event-fx-id f]
   (reg-event-fx event-fx-id nil f))
  ([event-fx-id coeffects f]
   (registry/reg-event-fx global-registry event-fx-id coeffects f)))

(defn reg-event-db
  ([event-id event-f]
   (reg-event-db event-id [] event-f))
  ([event-id interceptors event-f]
   (registry/reg-event-db global-registry event-id interceptors event-f)))

(defn clear-subscription-cache!
  [{:keys [store]}]
  (store/clear-subscription-cache! store))

(defn snapshot-sub
  [{:keys [store]} sub]
  (store/subscribe store sub))

(defn snapshot
  [{:keys [store]}]
  (store/snapshot store))

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
