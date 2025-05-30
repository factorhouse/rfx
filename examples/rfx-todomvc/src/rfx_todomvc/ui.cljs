(ns rfx-todomvc.ui
  (:require [io.factorhouse.hsx.core :as hsx]
            [io.factorhouse.rfx.core :as rfx]
            [io.factorhouse.rfx.dev :refer [wrap-dev]]
            ["react" :as react]
            ["react-dom/client" :refer [createRoot]]))

(defonce root
  (createRoot (.getElementById js/document "app")))

(defonce todo-context
  (wrap-dev (rfx/init {:initial-value {:todos {}}})))

(rfx/reg-event-db
 :todos/add
 (fn [db [_ todo]]
   (let [id (str (gensym "todo"))
         ts (js/Date.now)]
     (update db :todos assoc id {:value todo :ts ts :id id :completed? false}))))

(rfx/reg-event-db
 :todos/remove
 (fn [db [_ id]]
   (update db :todos dissoc id)))

(rfx/reg-sub
 :todos/view
 (fn [db _]
   (->> db :todos vals (sort-by :ts))))

(rfx/reg-sub
 :todos/view1
 [[:todos/view]]
 (fn [view _]
   (first view)))

(rfx/reg-sub
 :todos/view2
 [[:todos/view1]]
 (fn [_ _]
   :foo))

(rfx/reg-sub
 :todos/xyz
 (fn [db _]
   (->> db :todos vals (sort-by :ts))))

(defn todos-input []
  (let [dispatch (rfx/use-dispatch)]
    [:input {:className   "p-4 border border-slate-300"
             :placeholder "What needs to be done?"
             :on-key-down (fn [e]
                            (when (= "Enter" (.-key e))
                              (dispatch [:todos/add (-> e .-target .-value)])))}]))

(defn view-1 []
  (let [todos (rfx/use-sub [:todos/view1])]
    [:pre (pr-str todos)]))

(defn view-2 []
  (let [todos (rfx/use-sub [:todos/view2])]
    [:pre (pr-str todos)]))

(defn todos-list []
  (let [todos (rfx/use-sub [:todos/view])]
    [:div
     [view-1]
     [view-2]
     [:ul
      (for [item todos]
        ^{:key (str "todo-" (:id item))}
        [:div (:value item)])]]))

(defn hello-world []
  [:div {:className "p-4"}
   [:h1 {:className "text-7xl"}
    "todos!"]
   [todos-input]
   [todos-list]])

(defn init []
  (.render
   root
   (hsx/create-element
    [:> rfx/RfxContextProvider #js {"value" todo-context}
     [hello-world]])))

(init)
