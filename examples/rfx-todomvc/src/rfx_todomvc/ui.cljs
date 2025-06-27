(ns rfx-todomvc.ui
  (:require [io.factorhouse.hsx.core :as hsx]
            [io.factorhouse.rfx.core :as rfx]
            ["react" :as react]
            ["react-dom/client" :refer [createRoot]]))

(defonce root
  (createRoot (.getElementById js/document "app")))

(defonce todo-context
  (rfx/init {:initial-value {:todos {}}}))

(rfx/reg-event-db
 :todos/add
 (fn [db [_ todo]]
   (let [id (str (gensym "todo"))
         ts (js/Date.now)]
     (update db :todos assoc id {:value todo :ts ts :id id :completed? false}))))

(rfx/reg-event-db
 :todos/toggle
 (fn [db [_ todo-id]]
   (update-in db [:todos todo-id :completed?] not)))

(rfx/reg-event-db
 :todos/remove
 (fn [db [_ id]]
   (update db :todos dissoc id)))

(rfx/reg-sub
 :todos/view
 (fn [db _]
   (->> db :todos vals (sort-by :ts))))

(defn todos-input []
  (let [dispatch (rfx/use-dispatch)]
    [:input {:className   "w-full px-6 py-4 text-lg placeholder-gray-400 border-0 outline-none bg-white shadow-inner"
             :placeholder "What needs to be done?"
             :autoFocus   true
             :on-key-down (fn [e]
                            (when (= "Enter" (.-key e))
                              (let [value (-> e .-target .-value .trim)]
                                (when (seq value)
                                  (dispatch [:todos/add value])))))}]))

(defn todo-item [item]
  (let [dispatch (rfx/use-dispatch)]
    [:li {:className "group relative flex items-center px-6 py-3 border-b border-gray-100 bg-white hover:bg-gray-50 transition-colors"}
     [:button {:className "flex-shrink-0 mr-4 w-6 h-6 rounded-full border-2 border-gray-300 hover:border-green-500 transition-colors flex items-center justify-center"
               :on-click  (fn [_] (dispatch [:todos/toggle (:id item)]))}
      (when (:completed? item)
        [:svg {:className "w-4 h-4 text-green-500" :fill "currentColor" :viewBox "0 0 20 20"}
         [:path {:fillRule "evenodd" :d "M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" :clipRule "evenodd"}]])]
     [:span {:className (str "flex-1 text-lg "
                             (if (:completed? item)
                               "line-through text-gray-400"
                               "text-gray-800"))}
      (:value item)]
     [:button {:className "opacity-0 group-hover:opacity-100 ml-4 p-2 text-red-500 hover:text-red-700 transition-all"
               :on-click  (fn [_] (dispatch [:todos/remove (:id item)]))}
      [:svg {:className "w-5 h-5" :fill "currentColor" :viewBox "0 0 20 20"}
       [:path {:fillRule "evenodd" :d "M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" :clipRule "evenodd"}]]]]))

(defn todos-list []
  (let [todos (rfx/use-sub [:todos/view])]
    (when (seq todos)
      [:div {:className "bg-white shadow-lg"}
       [:ul
        (for [item todos]
          ^{:key (str "todo-" (:id item))}
          [todo-item item])]])))

(defn todo-mvc []
  [:div {:className "min-h-screen bg-gradient-to-br from-pink-50 to-blue-50 py-8"}
   [:div {:className "max-w-2xl mx-auto px-4"}
    [:header {:className "text-center mb-8"}
     [:h1 {:className "text-6xl font-thin text-gray-300 mb-2"}
      "todos"]]
    [:div {:className "bg-white shadow-2xl rounded-lg overflow-hidden"}
     [todos-input]
     [todos-list]]
    [:footer {:className "text-center mt-8 text-sm text-gray-400"}
     [:p "Created with " [:span {:className "text-red-500"} "♥"] " using RFX"]]]])

(defn init []
  (.render
   root
   (hsx/create-element
    [:> rfx/RfxContextProvider #js {"value" todo-context}
     [todo-mvc]])))

(init)
