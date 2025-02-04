(ns io.factorhouse.rfx.stores.atom
  (:require [io.factorhouse.rfx.store :as store]
            #?(:cljs ["react" :as react])))

(defn reaction
  [prev-cache next-db curr-registry cache store [sub-id & _sub-args :as sub]]
  (let [{:keys [sub-f signals]} (get-in curr-registry [:sub sub-id])]
    (if (contains? prev-cache sub)
      (if (seq signals)
        (let [prev-val         (get prev-cache sub)
              realized-signals (map #(reaction prev-cache next-db curr-registry cache store %) signals)
              signals-updated? (some first realized-signals)]
          (if signals-updated?
            (let [realized-signals (map second realized-signals)
                  realized-signals (if (= 1 (count realized-signals))
                                     (first realized-signals)
                                     realized-signals)
                  result           (sub-f realized-signals sub)]
              (swap! cache assoc sub result)
              [(not= result prev-val) result])
            (do
              (swap! cache assoc sub prev-val)
              [false prev-val])))

        ;; Else depends on app db, recompute result
        (let [result   (sub-f next-db sub)
              prev-val (get prev-cache sub)]
          (swap! cache assoc sub result)
          [(not= result prev-val) result]))

      ;; Else never seen subscription, compute from ground-up.
      [true (store/subscribe store sub)])))

(defn subscribe*
  [curr-cache curr-db curr-registry cache store [sub-id & _sub-args :as sub]]
  (if-let [{:keys [sub-f signals]} (get-in curr-registry [:sub sub-id])]
    (if-let [cache (get curr-cache sub)]
      cache
      (let [realized-signals (if (seq signals)
                               (if (= 1 (count signals))
                                 (subscribe* curr-cache curr-db curr-registry cache store (first signals))
                                 (into [] (map #(subscribe* curr-cache curr-db curr-registry cache store %)) signals))
                               curr-db)
            result           (sub-f realized-signals sub)]
        (swap! cache assoc sub result)
        result))))

(deftype RfxAtom
  [app-db listeners subscription-cache notify registry]
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
             (swap! listeners assoc id {:listener listener
                                        :sub      sub})
             (fn []
               (swap! listeners dissoc id))))
         (fn get-sub-snapshot* []
           (store/subscribe this sub)))

       :clj (throw (ex-info "use-sub cannot be used from within the JVM." {:sub sub}))))

  (snapshot-state [_] @app-db)

  (snapshot-reset! [this newval]
    #?(:cljs
       (when-let [notify* @notify]
         (js/clearTimeout notify*)))

    (let [prev-cache @subscription-cache]
      (reset! app-db newval)
      ;; - Wrapping `notify` in a setTimeout effectively allows us to 'debounce' multiple successive/rapid mutations
      ;; - This optimisation stops the `store/subscribe` and equality check from being called multiple times
      #?(:cljs
         (let [notify* (js/setTimeout
                         (fn []
                           (reset! notify nil)
                           (reset! subscription-cache {})
                           (let [curr-listeners (vals @listeners)
                                 curr-subs      (into #{} (map :sub) curr-listeners)
                                 curr-registry  @registry
                                 sub-notify?    (into {} (map (fn [sub]
                                                                (let [[notify? _] (reaction prev-cache newval curr-registry subscription-cache this sub)]
                                                                  [sub notify?])))
                                                      curr-subs)]
                             (doseq [{:keys [listener sub]} curr-listeners]
                               (when (sub-notify? sub)
                                 (listener))))))]
           (reset! notify notify*))))

    newval)

  #?@(:cljs
      (cljs.core/IDeref
        (-deref [this] (store/snapshot-state this)))

      :clj
      (clojure.lang.IDeref
        (deref [this] (store/snapshot-state this)))))

(defn store
  [registry initial-value]
  (let [app-db             (atom initial-value)
        listeners          (atom {})
        subscription-cache (atom {})
        notify             (atom nil)]
    (->RfxAtom app-db listeners subscription-cache notify registry)))
