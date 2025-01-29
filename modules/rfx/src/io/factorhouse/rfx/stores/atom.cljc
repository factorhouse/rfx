(ns io.factorhouse.rfx.stores.atom
  (:require [io.factorhouse.rfx.store :as store]
            #?(:cljs ["react" :as react])
            #?(:cljs [goog.async.nextTick])))

#?(:clj
   (extend-type clojure.lang.IAtom
     store/IStore
     (use-store [this f] (f @this))
     (snapshot-reset! [this newval] (reset! this newval))
     (snapshot-state [this] @this)))

#?(:cljs
   (defn reify-atom
     [initial-value]
     (let [app-db    (atom initial-value)
           listeners (atom {})
           notify    (atom nil)]
       (reify store/IStore
         (use-store [_ f]
           (react/useSyncExternalStore
             (fn [listener]
               (let [id (str (gensym "listener"))]
                 (swap! listeners assoc id listener)
                 (fn []
                   (swap! listeners dissoc id))))
             (fn []
               (f @app-db))))

         (snapshot-state [_] @app-db)

         (snapshot-reset! [_ newval]
           (when-let [v @notify]
             (prn "Clearing notify")
             (js/clearTimeout v))

           (reset! app-db newval)

           (reset! notify
                   (js/setTimeout (fn []
                                    (doseq [[_ f] @listeners]
                                      (f))
                                    (reset! notify nil))
                                  0)))

         cljs.core/IDeref
         (-deref [_] @app-db)))))

(defn store [initial-value]
  #?(:cljs (reify-atom initial-value)
     :clj  (atom initial-value)))
