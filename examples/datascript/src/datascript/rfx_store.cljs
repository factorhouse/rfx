(ns datascript.rfx-store
  (:require ["react" :as react]
            [datascript.core :as d]
            [io.factorhouse.rfx.core :as rfx]
            [io.factorhouse.rfx.store :as store]))

;; An incredibly naive impl of subscribe, good enough for a demo
(defn datascript-subscribe*
  [curr-registry db sub]
  (if-let [{:keys [sub-f signals]} (get-in curr-registry [:sub (first sub)])]
    (let [realized-signals (if (seq signals)
                             (if (= 1 (count signals))
                               (datascript-subscribe* curr-registry db (first signals))
                               (into [] (map #(datascript-subscribe* curr-registry db %)) signals))
                             db)]
      (sub-f realized-signals sub))))

(defn datascript-store
  [registry initial-value]
  (let [conn      (d/create-conn initial-value)
        listeners (atom {})
        cache     (atom {})]
    (reify store/IStore
      (subscribe [_ sub]
        (if-let [result (get @cache sub)]
          result
          (let [curr-registry @registry
                db            @conn
                result        (datascript-subscribe* curr-registry db sub)]
            (swap! cache assoc sub result)
            result)))

      (use-sub [this sub]
        (react/useSyncExternalStore
         (fn subscribe-to-sub* [listener]
           (let [id (str (gensym "listener"))]
             (swap! listeners assoc id {:listener listener :sub sub})
             (fn []
               (swap! listeners dissoc id))))
         (fn get-sub-snapshot* []
           (store/subscribe this sub))))

      (next-state! [_ tx-data]
        (d/transact! conn tx-data)
        (reset! cache {})
        (doseq [[_ {:keys [listener]}] @listeners]
          (listener)))

      (snapshot [_]
        @conn))))

(defn init
  [schema]
  (rfx/init {:store         datascript-store
             :initial-value schema}))
