(ns io.factorhouse.rfx.dev
  (:require [clojure.string :as str]
            [goog.object :as obj]
            [io.factorhouse.hsx.core :as hsx]
            [io.factorhouse.rfx.core :as rfx]
            [io.factorhouse.rfx.queue :as queue]
            [io.factorhouse.rfx.queues.stable :as stable-queue]
            [io.factorhouse.rfx.stats :as stats]
            [io.factorhouse.rfx.store :as store]
            [sci.core :as sci]
            ["react" :as react]
            ["react-dom/client" :refer [createRoot]]
            ["@headlessui/react" :refer [Tab TabGroup TabList TabPanel TabPanels]]
            ["@xyflow/react" :refer [ReactFlow Handle Position]]
            ["@dagrejs/dagre" :as Darge]
            ["dayjs" :as dayjs]
            ["dayjs/plugin/relativeTime" :as relativeTime]))


(.extend dayjs relativeTime)

(defn ago
  [ts]
  (.from (dayjs ts) (dayjs)))

(defn component-display-name []
  (try
    (when-let [owner (some-> js/__REACT_DEVTOOLS_GLOBAL_HOOK__
                             (obj/get "renderers")
                             (seq)
                             (first)
                             (second)
                             (obj/get "getCurrentFiber"))]
      (let [owner (some-> (owner) .-type)]
        (or (.-displayName owner) (.-name owner))))
    (catch :default _)))

(defn get-system-theme []
  (let [media-query (js/window.matchMedia "(prefers-color-scheme: dark)")]
    (if (.-matches media-query) "dark" "light")))

(def initial-state
  {:event-log    []
   :error-log    []
   :snapshots    []
   :repl-history []
   :sub-log      {}
   :filters      {:sub {:show-inactive? true}}
   :epoch        []
   :open?        false
   :theme        (get-system-theme)})

(defn remove-rfx-dev-ns
  [xs]
  (into [] (remove (fn [[id _]] (= (namespace id) "io.factorhouse.rfx.dev"))) xs))

(defonce dev-context
  (rfx/init {:initial-value initial-state}))

(defonce AppContext
  (react/createContext nil))

(defonce AppContextProvider
  (.-Provider AppContext))

(defn use-app-context []
  (react/useContext AppContext))

(defn use-global-registry
  [f]
  (react/useSyncExternalStore
    (fn [listener]
      (let [id (keyword (str (gensym "listener")))]
        (add-watch rfx/global-registry id (fn [_ _ _ _] (listener)))
        (fn []
          (remove-watch rfx/global-registry id))))
    (fn []
      (f (deref rfx/global-registry)))))

(defn use-sci []
  (let [dispatch    (rfx/use-dispatch)
        app-context (use-app-context)
        opts        {:namespaces {'user {'dispatch  (:dispatch app-context)
                                         'subscribe #(store/subscribe (:store app-context) %)}}}]
    (fn [expr-str]
      (sci/eval-string expr-str opts))))

(defn watch-system-theme
  [callback]
  (let [media-query (js/window.matchMedia "(prefers-color-scheme: dark)")]
    (.addEventListener media-query "change"
                       (fn [event]
                         (callback (if (.-matches event) "dark" "light"))))))

(let [dispatch (:dispatch dev-context)]
  (watch-system-theme #(dispatch [::set-ui-theme %])))

(defn class-names
  [& xs]
  (str/join " " xs))

(rfx/reg-sub
  ::sub-filters
  (fn [db _]
    (-> db :filters :sub)))

(rfx/reg-sub
  ::sub-filter
  [[::sub-filters]]
  (fn [sub-filters [_ id]]
    (get sub-filters id)))

(rfx/reg-event-db
  ::update-sub-filter
  (fn [db [_ k v]]
    (assoc-in db [:filters :sub k] v)))

(rfx/reg-event-db
  ::repl-result
  (fn [db [_ result]]
    (update db :repl-history conj result)))

(rfx/reg-sub
  ::repl-history
  (fn [db _]
    (:repl-history db)))

(rfx/reg-sub
  ::ui-theme
  (fn [db _]
    (:theme db)))

(rfx/reg-event-db
  ::set-ui-theme
  (fn [db [_ next-theme]]
    (assoc db :theme next-theme)))

(rfx/reg-sub
  ::open?
  (fn [db _]
    (:open? db)))

(rfx/reg-event-db
  ::open
  (fn [db [_ next-val]]
    (assoc db :open? next-val)))

(defn now []
  (js/Date.now))

(rfx/reg-event-db
  ::_increment-epoch
  (fn [db [_ render-perf]]
    (update db :epoch conj render-perf)))

(rfx/reg-sub
  ::_epoch
  (fn [db _]
    (:epoch db)))

(rfx/reg-sub
  ::_current_epoch
  [[::_epoch]]
  (fn [epoch _]
    (count epoch)))

(rfx/reg-sub
  ::_epoch-perf
  [[::_epoch]]
  (fn [epoch _]
    (stats/stats epoch)))

(rfx/reg-event-db
  ::_mark-event
  (fn [db [_ event]]
    (update db :event-log conj event)))

(rfx/reg-event-db
  ::_mark-sub
  (fn [db [_ id sub ts display-name]]
    (update-in db [:sub-log (first sub) :watchers]
               (fn [watchers]
                 (assoc watchers id {:last-observed   ts
                                     :first-observed  ts
                                     :display-name    display-name
                                     :render-count    0
                                     :force-re-render 0
                                     :id              id
                                     :args            (vec (rest sub))})))))

(rfx/reg-event-db
  ::_mark-sub-re-render
  (fn [db [_ id sub ts]]
    (update-in db [:sub-log (first sub) :watchers id]
               (fn [watcher]
                 (-> watcher
                     (update :render-count #(inc (or % 0)))
                     (assoc :last-observed ts))))))

(rfx/reg-event-db
  ::_unmark-sub
  (fn [db [_ id sub]]
    (update-in db [:sub-log (first sub) :watchers]
               (fn [watchers]
                 (dissoc watchers id)))))

(rfx/reg-sub
  ::_event-log
  (fn [db _]
    (:event-log db)))

(rfx/reg-sub
  ::_error-log
  (fn [db _]
    (:error-log db)))

(rfx/reg-sub
  ::_sub-log
  (fn [db _]
    (:sub-log db)))

(rfx/reg-sub
  ::_sub-force-re-render
  [[::_sub-log]]
  (fn [sub-log [_ sub id]]
    (get-in sub-log [(first sub) :watchers id :force-re-render])))

(rfx/reg-event-db
  ::_sub-force-re-render
  (fn [db [_ sub id]]
    (update-in db [(:sub-log first sub) :watchers id :force-re-render] inc)))

(rfx/reg-sub
  ::sub-users
  (fn [db [_ sub-id]]
    (sort-by :last-observed > (vals (get-in db [:sub-log sub-id :watchers])))))

(defn trace-queue
  [queue dev-dispatch]
  (reify queue/IEventQueue
    (push
      [_ event]
      (dev-dispatch [::_mark-event {:type  :push
                                    :event event
                                    :ts    (now)}])
      (queue/push queue event))

    (add-post-event-callback
      [_ id callback-fn]
      (dev-dispatch [::_mark-event {:type        :add-post-event-callback
                                    :id          id
                                    :ts          (now)
                                    :callback-fn callback-fn}])
      (queue/add-post-event-callback queue id callback-fn))

    (remove-post-event-callback
      [_ id]
      (dev-dispatch [::_mark-event {:type :remove-post-event-callback
                                    :id   id
                                    :ts   (now)}])
      (queue/remove-post-event-callback queue id))

    (purge [_]
      (dev-dispatch [::_mark-event {:type :purge
                                    :ts   (now)}])
      (queue/purge queue))))

(defn trace-error-handler
  [app-error-handler _dispatch]
  (fn [e]
    (rfx/log-and-continue-error-handler e)
    (app-error-handler e)))

(defn trace-store
  [store dev-dispatch use-dev-sub]
  (reify store/IStore
    (use-sub [_ sub]
      (let [[id _] (react/useState (str (gensym "sub")))
            _ (use-dev-sub [::_sub-force-re-render sub id])]

        (react/useEffect
          (fn []
            (dev-dispatch [::_mark-sub id sub (now) (component-display-name)])
            (fn []
              (dev-dispatch [::_unmark-sub id sub])))
          #js [])

        (react/useEffect
          (fn []
            (dev-dispatch [::_mark-sub-re-render id sub (now)])
            (constantly nil)))

        (store/use-sub store sub)))

    (snapshot-state [_]
      (store/snapshot-state store))

    (snapshot-reset! [_ newval]
      (prn "Reset?")
      (let [start  (js/performance.now)
            newval (store/snapshot-reset! store newval)
            end    (js/performance.now)]
        (dev-dispatch [::_increment-epoch (- end start)])
        newval))))

(defn ensure-rfx-dev! []
  (let [body     (.-body js/document)
        existing (.getElementById js/document "rfx-dev")]
    (when (nil? existing)
      (let [div (.createElement js/document "div")]
        (set! (.-id div) "rfx-dev")
        (.appendChild body div)))))

(ensure-rfx-dev!)

(defonce root
  (createRoot (.getElementById js/document "rfx-dev")))

(defn rfx-icon []
  (let [theme    (rfx/use-sub [::ui-theme])
        dispatch (rfx/use-dispatch)]
    [:div {:className theme}
     [:div {:className (class-names "fixed bottom-4 right-4 p-3 rounded-full shadow-lg cursor-pointer border-2"
                                    "transition-all duration-300 hover:shadow-[0_0_10px_#5A9E4B] hover:border-[#5A9E4B]"
                                    "bg-white text-gray-800 border-gray-300 dark:bg-gray-800 dark:text-white dark:border-gray-700")
            :on-click  #(dispatch [::open true])}
      "Rfx"]]))

(def rfx-slideout-class
  "fixed bottom-0 left-0 w-full max-h-[50vh] bg-white dark:bg-gray-900 dark:text-white
   border-t border-gray-300 dark:border-gray-700 shadow-lg h-[650px]")

(def rfx-primary-tabs-container-class
  "p-2 bg-gray-100 dark:bg-gray-900 border border-gray-300 dark:border-gray-700 rounded-t-md shadow flex w-full")

(def rfx-primary-tab-class
  "px-4 py-2 font-semibold text-sm rounded-md transition-all duration-200
   border border-transparent hover:bg-gray-200 dark:hover:bg-gray-700
   data-[selected]:bg-white dark:data-[selected]:bg-gray-800
   data-[selected]:text-blue-600 dark:data-[selected]:text-blue-400
   data-[selected]:border-gray-300 dark:data-[selected]:border-gray-600 cursor-pointer")

(def rfx-secondary-tabs-container-class
  "bg-gray-200 dark:bg-gray-800 border-t border-gray-300 dark:border-gray-700 p-1 flex flex")

(def rfx-secondary-tab-class
  "px-3 py-1 text-xs font-medium rounded-md transition-all duration-200
   text-gray-600 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-700
   data-[selected]:bg-gray-300 dark:data-[selected]:bg-gray-700
   data-[selected]:text-blue-600 dark:data-[selected]:text-blue-400 cursor-pointer")

(def rfx-content-class
  "bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-700
   rounded-b-md shadow-md p-4 overflow-auto h-full")

(def rfx-primary-button-class
  "px-4 py-2 rounded-md text-sm font-semibold transition-all duration-200
   bg-blue-600 text-white shadow-md hover:bg-blue-700
   dark:bg-blue-500 dark:hover:bg-blue-400 dark:text-white
   focus:outline-none focus:ring-2 focus:ring-blue-500 cursor-pointer")

(def rfx-secondary-button-class
  "px-4 py-2 rounded-md text-sm font-semibold transition-all duration-200
   bg-gray-200 text-gray-800 shadow-sm hover:bg-gray-300
   dark:bg-gray-700 dark:hover:bg-gray-600 dark:text-white
   focus:outline-none focus:ring-2 focus:ring-gray-500 cursor-pointer")

(def rfx-input-class
  "w-full px-3 py-2 rounded-md text-sm font-medium transition-all duration-200
   border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-gray-800 dark:text-white
   shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
   placeholder-gray-400 dark:placeholder-gray-500")

(def rfx-table-header-class
  "bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300
   font-semibold uppercase tracking-wider text-left border-b border-gray-300 dark:border-gray-700")

(def rfx-table-class
  "w-full text-sm border border-gray-300 dark:border-gray-700
   bg-white dark:bg-gray-900 text-gray-800 dark:text-white
   rounded-md shadow-sm overflow-hidden")

(def rfx-table-row-class
  "border-b border-gray-200 dark:border-gray-700
   hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors duration-200")

(def rfx-checkbox-class
  "w-4 h-4 rounded border border-gray-400 dark:border-gray-600
   bg-white dark:bg-gray-900 text-blue-600 dark:text-blue-400
   focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400
   transition-all duration-200 cursor-pointer
   checked:bg-blue-600 dark:checked:bg-blue-500 checked:border-transparent")

(defn is-sub-live
  [id]
  (let [active-watchers (rfx/use-sub [::sub-users id])
        total-watchers  (reduce + 0 (keep :render-count active-watchers))]
    (if (seq active-watchers)
      [:div {:className "mt-4"}
       [:div {:className "flex items-center gap-2"}
        [:span {:className "dark:bg-green-600 bg-green-100 rounded-md rounded p-2"}
         (count active-watchers) " subscribers"]
        [:span {:className "dark:bg-gray-600 bg-gray-100 rounded-md rounded p-2"}
         total-watchers " total re-renders"]]
       [:table {:className (class-names "mt-4" rfx-table-class)}
        [:caption {:className "sr-only"}
         (str "A table describing the active subscribers to " (pr-str id) ".")]
        [:thead {:className rfx-table-header-class}
         [:th {:className "px-3 py-2"} "Component"]
         [:th {:className "px-3 py-2"} "Subscription"]
         [:th {:className "px-3 py-2"} "Last rendered"]
         [:th {:className "px-3 py-2"} "Re-renders"]]
        [:tbody
         (for [watcher active-watchers]
           ^{:key (str "watcher-" (:id watcher))}
           [:tr {:className rfx-table-row-class}
            [:td {:className "px-3 py-2 whitespace-nowrap"}
             (or (:display-name watcher)
                 "Unknown")]
            [:td {:className "px-3 py-2 whitespace-nowrap"}
             [:pre "(subscribe " (pr-str (into [id] (:args watcher))) ")"]]
            [:td {:className "px-3 py-2 whitespace-nowrap"} (ago (:last-observed watcher))]
            [:td {:className "px-3 py-2 whitespace-nowrap"} (:render-count watcher)]])]]]

      [:div {:className "mt-4"}
       [:span {:className "dark:bg-yellow-600 bg-yellow-100 rounded-md rounded p-2"}
        "Inactive"]])))

(defn send-to-repl-button
  [sub-id]
  (let [dispatch    (rfx/use-dispatch)
        eval-string (use-sci)
        expr-string (str "(subscribe " (pr-str [sub-id]) ")")]
    [:div
     [:button {:className rfx-secondary-button-class
               :on-click  #(dispatch [::repl-result {:expr-str expr-string
                                                     :result   (eval-string expr-string)
                                                     :id       (str (gensym "repl"))}])}
      "Send to REPL"]]))

(defn registry-sub-form
  []
  (let [checked? (rfx/use-sub [::sub-filter :show-inactive?])
        dispatch (rfx/use-dispatch)]
    [:form {:className "flex items-center gap-4 w-full"}
     [:label {:className "flex items-center space-x-2 cursor-pointer"}
      [:input {:type      "checkbox"
               :className rfx-checkbox-class
               :checked   checked?
               :on-change #(dispatch [::update-sub-filter :show-inactive? (not checked?)])}]
      [:span {:className "text-sm text-gray-800 dark:text-gray-300"} "Show inactive?"]]]))

(defn registry-list
  [reg-id]
  (let [registry (use-global-registry reg-id)]
    [:div {:className rfx-content-class}
     (when (= :sub reg-id)
       [registry-sub-form])
     [:div {:className "flex items-center gap-4 w-full"}
      (if (seq registry)
        [:ul {:className "w-full"}
         (for [[id val] (remove-rfx-dev-ns registry)]
           ^{:key (str "registry-item-" id)}
           [:li {:className "rounded rounded-md dark:bg-slate-500 p-4 mt-4 dark:border-gray-700 border-gray-300 border w-full"}
            [:div {:className "flex items-center"}
             [:div {:className "grow"}
              [:h3 {:className "text-xl font-mono"}
               (pr-str id)]

              [:pre {:className "mt-2"}
               (pr-str val)]

              (when (= :sub reg-id)
                [is-sub-live id])]

             [send-to-repl-button id]]])]
        [:div {:className "rounded rounded-md dark:bg-yellow-600 bg-yellow-100 p-4 mt-4 dark:border-gray-700 border-gray-300 border w-full"}
         "You have no " (pr-str reg-id) " in your Rfx registry."])]]))

(defn registry-view []
  [:div {:className "w-full h-full"}
   [:> TabGroup {:as react/Fragment}
    [:div {:className rfx-secondary-tabs-container-class}
     [:> TabList {:className "flex space-x-2"}
      [:> Tab {:className rfx-secondary-tab-class}
       "Subscriptions"]
      [:> Tab {:className rfx-secondary-tab-class}
       "Events"]
      [:> Tab {:className rfx-secondary-tab-class}
       "Fx"]
      [:> Tab {:className rfx-secondary-tab-class}
       "Cofx"]]]
    [:> TabPanels {:className "w-full h-full"}
     [:> TabPanel {:className "h-full"}
      [registry-list :sub]]
     [:> TabPanel {:className "h-full"}
      [registry-list :event]]
     [:> TabPanel {:className "h-full"}
      [registry-list :fx]]
     [:> TabPanel {:className "h-full"}
      [registry-list :cofx]]]]])

(defn build-node
  [[id _]]
  {:id   (pr-str [id])
   :type "sub"
   :data {:label (pr-str [id])}})

(defn build-edges
  [[id {:keys [signals]}]]
  (into [{:id     (str id "-db")
          :source "db"
          :target (pr-str [id])}]
        (map (fn [signal]
               {:id     (str id "-" signal)
                :source (pr-str [id])
                :target (pr-str signal)})
             signals)))

(defn create-graph []
  (let [Graph (obj/get Darge/graphlib "Graph")]
    (doto (new Graph)
      (.setDefaultEdgeLabel (fn [] #js {})))))

(defn render-db-node [x]
  (hsx/create-element
    [:div {:className "p-2 border font-mono dark:border-gray-900 border-gray-300 dark:bg-slate-500 rounded rounded-md"}
     [:div "App DB"
      [:> Handle #js {"type"          "source"
                      "position"      (obj/get Position "Bottom")
                      "isConnectable" true}]]]))

(defn render-sub-node [x]
  (hsx/create-element
    [:div {:className "p-2 border font-mono dark:border-gray-900 border-gray-300 dark:bg-slate-500 rounded rounded-md"}
     [:div (obj/get x "id")
      [:> Handle #js {"type"          "target"
                      "position"      (obj/get Position "Top")
                      "isConnectable" true}]]]))

(defn live-view []
  (let [subs (use-global-registry :sub)
        subs (remove-rfx-dev-ns subs)
        [nodes set-nodes] (react/useState [])
        [edges set-edges] (react/useState [])]

    (react/useEffect
      (fn []
        (let [g (create-graph)]
          (.setGraph g #js {"rankdir" "TB"})
          (let [next-nodes (into [{:id   "db"
                                   :type "db"
                                   :data {:label "db"}}]
                                 (map build-node)
                                 subs)
                next-edges (mapcat build-edges subs)]

            (js/console.log "g" g)

            (doseq [{:keys [source target]} next-edges]
              (.setEdge g source target))

            (doseq [{:keys [id] :as node} next-nodes]
              (.setNode g id (clj->js (assoc node :width (* 10 (count id)) :height 42))))

            (Darge/layout g)

            (let [next-nodes (into [] (map (fn [{:keys [id] :as node}]
                                             (let [position (.node g id)
                                                   x        (obj/get position "x")
                                                   y        (obj/get position "y")]
                                               (assoc node :position {:x (- x (/ (* 9 (count id)) 2))
                                                                      :y (- y 21)}))))
                                   next-nodes)]
              (set-nodes (clj->js next-nodes))
              (set-edges (clj->js next-edges)))))
        (constantly nil))
      #js [(count subs)])

    [:div {:className (class-names rfx-content-class "w-full h-screen")}
     [:> ReactFlow {:nodes     (clj->js nodes)
                    :edges     (clj->js edges)
                    :nodeTypes #js {"db"  render-db-node
                                    "sub" render-sub-node}}]]))

(def rfx-repl-input-class
  "w-full px-3 py-2 text-sm border-t border-gray-300 dark:border-gray-700
   bg-white dark:bg-gray-900 text-gray-800 dark:text-white
   focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400
   outline-none")

(defn repl-input []
  (let [[input-value set-input-value] (react/useState "")
        eval-string (use-sci)
        dispatch    (rfx/use-dispatch)]
    [:input {:className   rfx-input-class
             :value       input-value
             :on-change   #(set-input-value (-> % .-target .-value))
             :on-key-down (fn [e]
                            (when (= "Enter" (.-key e))
                              (let [expr-str (-> e .-target .-value)
                                    result   (eval-string expr-str)]
                                (dispatch [::repl-result {:expr-str expr-str
                                                          :result   result
                                                          :id       (str (gensym "repl"))}]))))}]))

(def rfx-repl-container-class
  "flex flex-col h-[450px] w-full bg-white dark:bg-gray-900 text-gray-800 dark:text-white")

(def rfx-repl-history-class
  "flex-1 overflow-y-auto p-4 space-y-2 border-t border-gray-300 dark:border-gray-700")

(defn repl-view
  []
  (let [results (rfx/use-sub [::repl-history])]
    [:div {:className rfx-content-class}
     [:div {:className rfx-repl-container-class}
      [:div {:className rfx-repl-history-class}
       [:ul
        (for [{:keys [expr-str id result]} results]
          ^{:key (str "repl-result-" id)}
          [:li {:className "rounded rounded-md dark:bg-slate-500 p-4 mt-4 dark:border-gray-700 border-gray-300 border w-full"}
           [:div (str "user => " expr-str)]
           [:pre (pr-str result)]])]]
      [:div {}
       [repl-input]]]]))

(rfx/reg-event-db
  ::_snapshot
  (fn [db [_ snapshot]]
    (update db :snapshots conj snapshot)))

(rfx/reg-sub
  ::_snapshots
  (fn [db _]
    (:snapshots db)))

(defn snapshots-view []
  (let [dispatch    (rfx/use-dispatch)
        snapshots   (rfx/use-sub [::_snapshots])
        app-context (use-app-context)]
    [:div {:className rfx-content-class}
     [:button {:on-click  #(dispatch [::_snapshot {:db (store/snapshot-state (:store app-context))
                                                   :id (str (gensym "snapshot"))
                                                   :ts (now)}])
               :className rfx-primary-button-class}
      "Create snapshot"]
     [:ul
      (for [snapshot snapshots]
        ^{:key (str "snapshot-" (:id snapshot))}
        [:li {:className "p-2 border mt-4"}
         [:div {} (str "Snapshot " (:id snapshot))
          [:div {:className "flex items-center gap-2 mt-2"}
           [:button {:on-click  #(store/snapshot-reset! (:store app-context) (:db snapshot))
                     :className rfx-secondary-button-class}
            "Restore"]
           [:button {:on-click  #(store/snapshot-reset! (:store app-context) (:db snapshot))
                     :className rfx-secondary-button-class}
            "Download"]]]])]]))

(defn store-view
  []
  (let [current-epoch (rfx/use-sub [::_current_epoch])
        stats         (rfx/use-sub [::_epoch-perf])]
    [:div
     [:p "Curent epoch " current-epoch]
     [:pre (pr-str stats)]]))

(defn rfx-slide []
  (let [theme    (rfx/use-sub [::ui-theme])
        open?    (rfx/use-sub [::open?])
        dispatch (rfx/use-dispatch)]
    (when open?
      [:div {:className theme}
       [:div {:className rfx-slideout-class}
        [:div {:className "p-4 h-full"}
         [:> TabGroup {:as react/Fragment}
          [:div {:className rfx-primary-tabs-container-class}
           [:> TabList {:className "flex space-x-2 grow"}
            [:> Tab {:className rfx-primary-tab-class}
             "Live view"]
            [:> Tab {:className rfx-primary-tab-class}
             "Store"]
            [:> Tab {:className rfx-primary-tab-class}
             "Registry"]
            [:> Tab {:className rfx-primary-tab-class}
             "Snapshots"]
            [:> Tab {:className rfx-primary-tab-class}
             "Log"]
            [:> Tab {:className rfx-primary-tab-class}
             "REPL"]]
           [:button {:on-click  #(dispatch [::set-ui-theme (if (= theme "dark")
                                                             "light"
                                                             "dark")])
                     :className (class-names "justify-self-end mr-4" rfx-primary-button-class)}
            [:span {:className "sr-only"} "Toggle theme"]
            "T"]
           [:button {:on-click  #(dispatch [::open false])
                     :className (class-names "justify-self-end mr-4" rfx-primary-button-class)}
            [:span {:className "sr-only"} "Close"]
            "X"]]
          [:> TabPanels {:className "h-full"}
           [:> TabPanel {:className "h-full"}
            [live-view]]
           [:> TabPanel {:className "h-full"}
            [store-view]]
           [:> TabPanel {:className "h-full"}
            [registry-view]]
           [:> TabPanel {:className "h-full"}
            [snapshots-view]]
           [:> TabPanel {:className "h-full"}
            "Log"]
           [:> TabPanel {:className "h-full"}
            [repl-view]]]]]]])))

(defn init!
  [app-context]
  (.render root
           (hsx/create-element
             [:> AppContextProvider #js {"value" app-context}
              [:> rfx/RfxContextProvider #js {"value" dev-context}
               [:<>
                [rfx-icon]
                [rfx-slide]]]])))

(defn ^:export wrap-dev
  [app-context]
  (let [dev-dispatch (:dispatch dev-context)
        use-dev-sub  (:use-sub dev-context)]
    (init! app-context)
    (let [next-ctx      (-> app-context
                            (update :store trace-store dev-dispatch use-dev-sub)
                            (update :error-handler trace-error-handler dev-dispatch))
          handler       (rfx/handler rfx/global-registry (:store next-ctx) (:error-handler next-ctx))
          queue         (trace-queue (stable-queue/event-queue handler (:error-handler next-ctx))
                                     dev-dispatch)
          use-sub       (fn trace-use-sub* [sub]
                          (store/use-sub (:store next-ctx) sub))
          next-ctx      (assoc next-ctx :use-sub use-sub
                                        :handler handler
                                        :queue queue)
          dispatch      (fn [event]
                          (rfx/dispatch next-ctx event))
          dispatch-sync (fn [event]
                          (handler queue event))]
      (assoc next-ctx :dispatch dispatch :dispatch-sync dispatch-sync))))
