(ns io.factorhouse.rfx.stores.atom
  (:require [io.factorhouse.rfx.store :as store]
            #?(:cljs ["react" :as react])))

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
         (fn [listener]
           (let [id (str (gensym "listener"))]
             (swap! listeners assoc id {:listener listener
                                        :sub      sub})
             (fn []
               (swap! listeners dissoc id))))
         (fn []
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
         (let [notify*   (js/setTimeout
                           (fn []
                             (reset! notify nil)
                             (reset! subscription-cache {})
                             ;; - When an external store emits a change, the hook triggers a re-render.
                             ;; - React batches these updates, processing them together in a single render cycle if multiple changes occur rapidly.
                             (doseq [[_ {:keys [listener sub]}] @listeners]
                               (when-not (= (store/subscribe this sub)
                                            (get prev-cache sub))
                                 (listener)))))]
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
