(ns io.factorhouse.rfx.stores.atom
  (:require [io.factorhouse.rfx.store :as store]
            #?(:cljs ["react" :as react])))

;; Runs the 'reaction' logic: checks whether a subscription value has changed between state changes.
;; Returns a tuple of [state-updated? next-val]
(defn- reaction
  [prev-cache next-db curr-registry cache cache-diff store sub]
  (if-let [{:keys [sub-f signals]} (get-in curr-registry [:sub (first sub)])]
    (cond
      ;; Already seen subscription, just return previously computed reaction
      (contains? @cache-diff sub)
      [(get @cache-diff sub) (get @cache sub)]

      ;; Sub was previously in cache, calculate whether we need to re-compute value
      (contains? prev-cache sub)
      (if (seq signals)
        (let [prev-val         (get prev-cache sub)
              realized-signals (map #(reaction prev-cache next-db curr-registry cache cache-diff store %) signals)
              signals-updated? (some first realized-signals)]
          (if signals-updated?
            ;; If any of the signals have updated, recompute the value
            (let [realized-signals (mapv second realized-signals)
                  realized-signals (if (= 1 (count realized-signals))
                                     (first realized-signals)
                                     realized-signals)
                  result           (or (get @cache sub) (sub-f realized-signals sub))
                  diff?            (or (get @cache-diff sub)
                                       (not= result prev-val))]
              (vswap! cache-diff assoc sub diff?)
              (vswap! cache assoc sub result)
              [diff? result])
            ;; Else, signals haven't updated, return previous value
            (do
              (vswap! cache-diff assoc sub false)
              (vswap! cache assoc sub prev-val)
              [false prev-val])))

        ;; Sub depends solely on app db, always recompute result
        (let [result   (or (get @cache sub) (sub-f next-db sub))
              prev-val (get prev-cache sub)
              diff?    (or (get @cache-diff sub)
                           (not= result prev-val))]
          (vswap! cache-diff assoc sub diff?)
          (vswap! cache assoc sub result)
          [diff? result]))

      :else
      ;; Never seen subscription, compute from ground-up.
      (do
        (vswap! cache-diff assoc sub true)
        [true (store/subscribe store sub)]))

    (throw (ex-info "Subscription does not exist in registry." {:sub sub}))))

(defn- subscribe*
  [curr-cache curr-db curr-registry cache store sub]
  (if-let [{:keys [sub-f signals]} (get-in curr-registry [:sub (first sub)])]
    (if-let [cache (get curr-cache sub)]
      cache
      (let [realized-signals (if (seq signals)
                               (if (= 1 (count signals))
                                 (subscribe* curr-cache curr-db curr-registry cache store (first signals))
                                 (into [] (map #(subscribe* curr-cache curr-db curr-registry cache store %)) signals))
                               curr-db)
            result           (sub-f realized-signals sub)]
        (vswap! cache assoc sub result)
        result))
    (throw (ex-info "Subscription does not exist in registry." {:sub sub}))))

(deftype RfxAtom
  [app-db listeners subscription-cache registry]
  store/IStore
  (subscribe [this sub]
    (let [curr-cache    @subscription-cache
          curr-db       @app-db
          curr-registry @registry]
      (subscribe* curr-cache curr-db curr-registry subscription-cache this sub)))

  (use-sub [this sub]
    #?(:cljs
       (react/useSyncExternalStore
         (fn subscribe-to-sub* [listener]
           (let [id (str (gensym "listener"))]
             (vswap! listeners assoc id {:listener listener :sub sub})
             (fn []
               (vswap! listeners dissoc id))))
         (fn get-sub-snapshot* []
           (store/subscribe this sub)))

       :clj (throw (ex-info "use-sub cannot be called from the JVM." {:sub sub}))))

  (snapshot-state [_] @app-db)

  (snapshot-reset! [this newval]
    (let [prev-cache @subscription-cache]
      (vreset! app-db newval)
      (vreset! subscription-cache {})
      (let [curr-listeners (vals @listeners)
            curr-subs      (into #{} (map :sub) curr-listeners)
            curr-registry  @registry
            cache-diff     (volatile! {})
            make-reaction  (fn [sub]
                             (reaction prev-cache newval curr-registry subscription-cache cache-diff this sub))
            sub-notify?    (into {} (map (fn [sub]
                                           (let [[notify? _] (make-reaction sub)]
                                             [sub notify?])))
                                 curr-subs)]
        (doseq [{:keys [listener sub]} curr-listeners]
          (when (sub-notify? sub)
            (listener)))))

    newval)

  #?@(:cljs
      (cljs.core/IDeref
        (-deref [this] (store/snapshot-state this)))

      :clj
      (clojure.lang.IDeref
        (deref [this] (store/snapshot-state this)))))

(defn store
  [registry initial-value]
  (let [app-db             (volatile! initial-value)
        listeners          (volatile! {})
        subscription-cache (volatile! {})]
    (->RfxAtom app-db listeners subscription-cache registry)))
