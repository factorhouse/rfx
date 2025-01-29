(ns io.factorhouse.rfx.core
  "An implementation of re-frame built for modern React"
  (:refer-clojure :exclude [error-handler])
  (:require [io.factorhouse.rfx.loggers :as loggers]
            [io.factorhouse.rfx.queue :as queue]
            [io.factorhouse.rfx.queues.stable :as queues.stable]
            [io.factorhouse.rfx.store :as store]))

(defonce ^:private world
  (atom
    {:store         nil
     :queue         nil
     :error-handler nil
     :registry      {}}))

(defn- app-db []
  (if-let [store (:store @world)]
    store
    (throw (ex-info "Rfx has not been initialized." {}))))

(defn- event-queue []
  (if-let [queue (:queue @world)]
    queue
    (throw (ex-info "Rfx has not been initialized." {}))))

(defn- error-handler
  [errors]
  (if-let [error-handler (:error-handler @world)]
    (error-handler errors)
    (throw (ex-info "Rfx has not been initialized." {}))))

(defn log-and-continue-error-handler
  [{:keys [errors]}]
  (doseq [[level message] errors]
    #?(:clj (loggers/log level message)
       :cljs (js/console.log "Hello => " (pr-str level) message))))

(defn dispatch
  [event]
  (if (nil? event)
    (throw (ex-info "re-frame: you called \"dispatch\" without an event vector." {}))
    (queue/push (event-queue) event))
  nil)

(defn reg-cofx
  [cofx-id cofx-fn]
  (swap! world assoc-in [:registry :cofx cofx-id] cofx-fn))

(defn inject-cofx
  ([id]
   {:id id :value nil})
  ([id value]
   {:id id :value value}))

(defn handle
  [[event-id & _args :as event]]
  (let [curr-registry (:registry @world)
        errors        (atom [])]
    (if-let [{:keys [event-f coeffects]} (get-in curr-registry [:event event-id])]
      (let [curr-state (store/snapshot-state (app-db))
            ctx        (reduce
                         (fn [ctx {:keys [id value]}]
                           (if-let [cofx-fn (get-in curr-registry [:cofx id])]
                             (cofx-fn ctx value)
                             (do
                               (swap! errors conj {:type    :missing-cofx
                                                   :level   :warn
                                                   :message (str "No such cofx named " (pr-str id) ". Returning previous context.")})
                               ctx)))
                         {:db curr-state}
                         coeffects)
            result     (event-f ctx event)]

        (when-let [next-db (:db result)]
          (let [db-fn (get-in curr-registry [:fx :db])]
            (db-fn next-db)))

        (doseq [[fx-id fx-val] (dissoc result :db)]
          (if-let [fx-fn (get-in curr-registry [:fx fx-id])]
            (fx-fn fx-val)
            (swap! errors conj {:type    :missing-fx
                                :level   :warn
                                :message (str "Cannot find fx named " (pr-str fx-id))}))))

      (swap! errors conj {:type    :missing-event
                          :level   :warn
                          :message (str "Cannot find event named " (pr-str event-id) ".")}))

    (when-let [errors (seq @errors)]
      (error-handler {:errors errors}))))

(defn reg-sub
  ([sub-id]
   (let [sub {:sub-f (fn [db _] db) :signals []}]
     (swap! world assoc-in [:registry :sub sub-id] sub)))
  ([sub-id sub-f]
   (let [sub {:sub-f sub-f :signals []}]
     (swap! world assoc-in [:registry :sub sub-id] sub)))
  ([sub-id signals sub-f]
   (let [sub {:sub-f sub-f :signals signals}]
     (swap! world assoc-in [:registry :sub sub-id] sub))))

(defn- subscribe*
  [curr-registry [sub-id & _sub-args :as sub] db]
  (if-let [{:keys [sub-f signals]} (get-in curr-registry [:sub sub-id])]
    (let [db-input (if (seq signals)
                     (if (= 1 (count signals))
                       (subscribe* curr-registry (first signals) db)
                       (into [] (map #(subscribe* curr-registry % db)) signals))
                     db)]
      (sub-f db-input sub))
    (error-handler
      {:errors
       [{:type    :subscribe-error
         :level   :warn
         :message (str "Cannot find subscription named " (pr-str sub-id) ".")}]})))

(defn snapshot-sub
  [sub]
  (subscribe* (:registry @world) sub (store/snapshot-state (app-db))))

(reg-cofx ::subscribe
          (fn [coeffects [sub-id & _sub-args :as sub]]
            (assoc coeffects sub-id (snapshot-sub sub))))

(defn use-sub
  [sub]
  (let [sub-f (partial subscribe* (:registry @world) sub)]
    (store/use-store (app-db) (with-meta sub-f {:sub sub}))))

(defn reg-fx
  [fx-id f]
  (swap! world assoc-in [:registry :fx fx-id] f))

(reg-fx :db
        (fn db-fx [next-value]
          (store/snapshot-reset! (app-db) next-value)))

(reg-fx :dispatch-n
        (fn dispatch-n-fx [dispatch-events]
          (doseq [event dispatch-events]
            (dispatch event))))

(reg-fx :dispatch
        (fn dispatch-fx [event]
          (dispatch event)))

(defn reg-event-fx
  ([event-fx-id f]
   (let [fx {:event-f f}]
     (swap! world assoc-in [:registry :event event-fx-id] fx)))
  ([event-fx-id coeffects f]
   (let [fx {:event-f f :coeffects coeffects}]
     (swap! world assoc-in [:registry :event event-fx-id] fx))))

(defn reg-error-fx
  ([event-fx-id f]
   (let [fx {:event-f f}]
     (swap! world update :registry
            (fn [curr-registry]
              (-> curr-registry
                  (assoc :error-event-id event-fx-id)
                  (assoc-in [:event event-fx-id] fx))))))
  ([event-fx-id coeffects f]
   (let [fx {:event-f f :coeffects coeffects}]
     (swap! world update :registry
            (fn [curr-registry]
              (-> curr-registry
                  (assoc :error-event-id event-fx-id)
                  (assoc-in [:event event-fx-id] fx)))))))

(defn reg-event-db
  [event-id event-f]
  (let [event {:event-f (fn [{:keys [db]} event] {:db (event-f db event)})}]
    (swap! world assoc-in [:registry :event event-id] event)))

(defn make-restore-fn []
  (let [prev-state (store/snapshot-state (app-db))]
    (fn []
      (store/snapshot-reset! (app-db) prev-state))))

(defn clear-subscription-cache! []
  (swap! world update :registry #(dissoc % :sub)))

(defn init!
  [{:keys [queue store error-handler]
    :or   {error-handler log-and-continue-error-handler
           queue         queues.stable/event-queue}}]
  (swap! world assoc :queue (queue handle error-handler) :store store :error-handler error-handler))
