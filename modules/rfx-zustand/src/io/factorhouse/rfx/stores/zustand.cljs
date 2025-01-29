(ns io.factorhouse.rfx.stores.zustand
  (:require ["zustand/traditional" :refer [createWithEqualityFn]]
            [goog.object :as obj]
            [io.factorhouse.rfx.store :as store]))

(defn store
  [initial-value]
  (let [store    (createWithEqualityFn (fn [set]
                           #js {:state initial-value
                                :reset (fn -reset! [next-state]
                                         (set (fn [_]
                                                #js {:reset -reset!
                                                     :state next-state})
                                              true))}))
        snapshot (obj/get store "getState")]
    (reify store/IStore
      (use-store [_ f]
        (store (fn [state] (f (obj/get state "state")))
               (fn [old-state new-state]
                 (= old-state new-state))))

      (snapshot-reset! [_ newval]
        (let [reset! (obj/get (snapshot) "reset")]
          (reset! newval)))

      (snapshot-state [_]
        (obj/get (snapshot) "state"))

      cljs.core/IDeref
      (-deref [this]
        (store/snapshot-state this)))))
