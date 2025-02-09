(ns io.factorhouse.rfx.dev.db
  (:require [io.factorhouse.rfx.core :as rfx]
            [io.factorhouse.rfx.dev.stats :as stats]
            [io.factorhouse.rfx.registry :as reg]
            ["react" :as react]))

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

(defonce registry
  (atom {}))

(defonce context
  (rfx/init {:initial-value initial-state
             :registry      registry}))

(defonce AppContext
  (react/createContext nil))

(defonce AppContextProvider
  (.-Provider AppContext))

(defn use-app-context []
  (react/useContext AppContext))

(reg/reg-sub registry ::sub-filters []
  (fn [db _]
    (-> db :filters :sub)))

(reg/reg-sub
  registry
  ::sub-filter
  [[::sub-filters]]
  (fn [sub-filters [_ id]]
    (get sub-filters id)))

(reg/reg-event-db
  registry
  ::update-sub-filter
  (fn [db [_ k v]]
    (assoc-in db [:filters :sub k] v)))

(reg/reg-event-db
  registry
  ::repl-result
  (fn [db [_ result]]
    (update db :repl-history conj result)))

(reg/reg-sub
  registry
  ::repl-history
  []
  (fn [db _]
    (:repl-history db)))

(reg/reg-sub
  registry
  ::ui-theme
  []
  (fn [db _]
    (:theme db)))

(reg/reg-event-db
  registry
  ::set-ui-theme
  (fn [db [_ next-theme]]
    (assoc db :theme next-theme)))

(reg/reg-sub
  registry
  ::open?
  []
  (fn [db _]
    (:open? db)))

(reg/reg-event-db
  registry
  ::open
  (fn [db [_ next-val]]
    (assoc db :open? next-val)))

(reg/reg-event-db
  registry
  ::increment-epoch
  (fn [db [_ render-perf]]
    (update db :epoch conj render-perf)))

(reg/reg-sub
  registry
  ::epoch
  []
  (fn [db _]
    (:epoch db)))

(reg/reg-sub
  registry
  ::current_epoch
  [[::epoch]]
  (fn [epoch _]
    (count epoch)))

(reg/reg-sub
  registry
  ::epoch-perf
  [[::epoch]]
  (fn [epoch _]
    (stats/stats epoch)))

(reg/reg-event-db
  registry
  ::mark-event
  (fn [db [_ event]]
    (update db :event-log conj event)))

(reg/reg-event-db
  registry
  ::mark-sub
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

(reg/reg-event-db
  registry
  ::mark-sub-re-render
  (fn [db [_ id sub ts]]
    (update-in db [:sub-log (first sub) :watchers id]
               (fn [watcher]
                 (-> watcher
                     (update :render-count #(inc (or % 0)))
                     (assoc :last-observed ts))))))

(reg/reg-event-db
  registry
  ::unmark-sub
  (fn [db [_ id sub]]
    (update-in db [:sub-log (first sub) :watchers]
               (fn [watchers]
                 (dissoc watchers id)))))

(reg/reg-sub
  registry
  ::event-log
  []
  (fn [db _]
    (:event-log db)))

(reg/reg-sub
  registry
  ::error-log
  []
  (fn [db _]
    (:error-log db)))

(reg/reg-sub
  registry
  ::sub-log
  []
  (fn [db _]
    (:sub-log db)))

(reg/reg-sub
  registry
  ::sub-force-re-render
  [[::sub-log]]
  (fn [sub-log [_ sub id]]
    (get-in sub-log [(first sub) :watchers id :force-re-render])))

(reg/reg-event-db
  registry
  ::sub-force-re-render
  (fn [db [_ sub id]]
    (update-in db [(:sub-log first sub) :watchers id :force-re-render] inc)))

(reg/reg-sub
  registry
  ::sub-users
  []
  (fn [db [_ sub-id]]
    (sort-by :last-observed > (vals (get-in db [:sub-log sub-id :watchers])))))

(reg/reg-event-db
  registry
  ::snapshot
  (fn [db [_ snapshot]]
    (update db :snapshots conj snapshot)))

(reg/reg-sub
  registry
  ::snapshots
  []
  (fn [db _]
    (:snapshots db)))
