(ns uix.rfx
  (:require [io.factorhouse.rfx.core :as rfx]
            [uix.core :refer [defui $]]
            [uix.dom]))

(rfx/reg-sub :counter/value (fn [db _] (:counter/value db 0)))
(rfx/reg-event-db :counter/increment (fn [db _] (update db :counter/value (fnil inc 0))))
(rfx/reg-event-db :counter/decrement (fn [db _] (update db :counter/value (fnil dec 0))))

(defui button [{:keys [on-click children]}]
  ($ :button.btn {:on-click on-click}
     children))

(defui app []
  (let [dispatch (rfx/use-dispatch)
        value    (rfx/use-sub [:counter/value])]
    ($ :<>
       ($ button {:on-click #(dispatch [:counter/increment])} "-")
       ($ :span value)
       ($ button {:on-click #(dispatch [:counter/decrement])} "+"))))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(uix.dom/render-root ($ app) root)
