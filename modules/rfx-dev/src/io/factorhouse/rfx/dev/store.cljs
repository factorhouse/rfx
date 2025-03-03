(ns io.factorhouse.rfx.dev.store
  (:require [io.factorhouse.rfx.dev.db :as db]
            [io.factorhouse.rfx.dev.util :as util]
            [io.factorhouse.rfx.store :as store]
            ["react" :as react]))

(defn trace-store
  [store dev-dispatch use-dev-sub]
  (reify store/IStore
    (use-sub [_ sub]
      (let [[id _] (react/useState (str (gensym "sub")))
            _ (use-dev-sub [::db/sub-force-re-render sub id])]

        (react/useEffect
          (fn []
            (dev-dispatch [::db/mark-sub id sub (util/now) (util/component-display-name)])
            (fn []
              (dev-dispatch [::db/unmark-sub id sub])))
          #js [])

        (react/useEffect
          (fn []
            (dev-dispatch [::db/mark-sub-re-render id sub (util/now)])
            (constantly nil)))

        (store/use-sub store sub)))

    (snapshot [_]
      (store/snapshot store))

    (next-state! [_ newval]
      (let [start  (js/performance.now)
            newval (store/next-state! store newval)
            end    (js/performance.now)]
        (dev-dispatch [::db/increment-epoch (- end start)])
        newval))))
