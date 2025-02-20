(ns re-frame-bridge-todomvc.ui
  (:require [io.factorhouse.hsx.core :as hsx]
            [re-frame.core :as rf]
            ["react" :as react]
            ["react-dom/client" :refer [createRoot]]))

(defonce root
  (createRoot (.getElementById js/document "app")))

(rf/reg-event-db
  :todos/add
  (fn [db [_ todo]]
    (let [id (str (gensym "todo"))
          ts (js/Date.now)]
      (update db :todos assoc id {:value todo :ts ts :id id :completed? false}))))

(rf/reg-event-db
  :todos/remove
  (fn [db [_ id]]
    (update db :todos dissoc id)))

(rf/reg-sub
  :todos/view
  (fn [db _]
    (->> db :todos vals (sort-by :ts))))

(rf/reg-sub
  :todos/view1
  [[:todos/view]]
  (fn [view _]
    (first view)))

(rf/reg-sub
  :todos/view2
  [[:todos/view1]]
  (fn [_ _]
    :foo))

(rf/reg-sub
  :todos/xyz
  (fn [db _]
    (->> db :todos vals (sort-by :ts))))

(defn todos-input []
  (let [dispatch (rf/use-dispatch)]
    [:input {:className   "p-4 border border-slate-300"
             :placeholder "What needs to be done?"
             :on-key-down (fn [e]
                            (when (= "Enter" (.-key e))
                              (dispatch [:todos/add (-> e .-target .-value)])))}]))

(defn view-1 []
  (let [todos (rf/subscribe [:todos/view1])]
    [:pre (pr-str @todos)]))

(defn view-2 []
  (let [todos (rf/subscribe [:todos/view2])]
    [:pre (pr-str @todos)]))

(defn todos-list []
  (let [todos (rf/subscribe [:todos/view])]
    [:div
     [view-1]
     [view-2]
     [:ul
      (for [item @todos]
        ^{:key (str "todo-" (:id item))}
        [:div (:value item)])]]))

(defn hello-world []
  [:div {:className "p-4"}
   [:h1 {:className "text-7xl"}
    "todos!"]
   [todos-input]
   [todos-list]])

(defn init []
  (.render root (hsx/create-element [hello-world])))

(init)
