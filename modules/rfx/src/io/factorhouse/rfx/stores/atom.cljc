(ns io.factorhouse.rfx.stores.atom
  (:require [io.factorhouse.rfx.store :as store]
            #?(:cljs ["react" :as react])))

(defn subscribe-rehydrate
  [prev-cache registry cache store [sub-id & _sub-args :as sub]]
  (when-let [{:keys [sub-f signals]} (get-in @registry [:sub sub-id])]
    (let [cached-val (get prev-cache sub)]
      (if (seq signals)
        (let [db-input          (into [] (map (fn [signal]
                                                (let [[_ next-val] (subscribe-rehydrate prev-cache registry cache store signal)]
                                                  [signal next-val])))
                                      signals)
              no-signal-change? (every? (fn [[signal next-val]]
                                          (= next-val (get prev-cache signal)))
                                        db-input)
              recompute-value (fn []
                                (let [db-input (map second db-input)
                                      db-input (if (= 1 (count db-input))
                                                 (first db-input)
                                                 db-input)
                                      result   (sub-f db-input sub)]
                                  (swap! cache assoc sub result)
                                  [(= result cached-val)
                                   result]))]
          ;; No signal change, get the previous value of sub (if present), otherwise recompute
          (if no-signal-change?
            (if cached-val
              (do (swap! cache assoc sub cached-val)
                  [false cached-val])
              ;; If we have no recorded cached-val, we need to recompute
              (recompute-value))
            ;; Signals have changed, recompute value
            (recompute-value)))
        ;; Else, sub depends directly on app-db, re-compute val
        (let [result (sub-f @store sub)]
          (swap! cache assoc sub result)
          [(= result cached-val)
           result])))))

(deftype RfxAtom
  [app-db listeners subscription-cache notify registry]
  store/IStore
  (subscribe [this [sub-id & _sub-args :as sub]]
    (if-let [{:keys [sub-f signals]} (get-in @registry [:sub sub-id])]
      (if-let [cache (get @subscription-cache sub)]
        cache
        (let [db-input (if (seq signals)
                         (if (= 1 (count signals))
                           (store/subscribe this (first signals))
                           (into [] (map #(store/subscribe this %)) signals))
                         @app-db)
              result   (sub-f db-input sub)]
          (swap! subscription-cache assoc sub result)
          result))))

  (use-sub [this sub]
    #?(:cljs
       (react/useSyncExternalStore
         (fn subscribe-to-sub* [listener]
           (let [id (str (gensym "listener"))]
             (swap! listeners assoc id {:listener listener
                                        :sub      sub})
             (fn []
               (swap! listeners dissoc id))))
         (fn get-store-snapshot* []
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
                           ;; - When an external store emits a change, the hook triggers a re-render.
                           ;; - React batches these updates, processing them together in a single render cycle if multiple changes occur rapidly.
                           (doseq [[_ {:keys [listener sub]}] @listeners]
                             (let [[notify? _] (prev-cache registry subscription-cache this sub)]
                               (when notify?
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
