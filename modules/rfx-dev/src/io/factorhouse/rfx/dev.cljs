(ns io.factorhouse.rfx.dev
  (:require [io.factorhouse.hsx.core :as hsx]
            [io.factorhouse.rfx.core :as rfx]
            [io.factorhouse.rfx.dev.db :as db]
            [io.factorhouse.rfx.dev.queue :as dev.queue]
            [io.factorhouse.rfx.dev.store :as dev.store]
            [io.factorhouse.rfx.dev.views :as views]
            [io.factorhouse.rfx.queues.stable :as stable-queue]
            [io.factorhouse.rfx.store :as store]
            ["react" :as react]
            ["react-dom/client" :refer [createRoot]]))

(defn watch-system-theme
  [callback]
  (let [media-query (js/window.matchMedia "(prefers-color-scheme: dark)")]
    (.addEventListener media-query "change"
                       (fn [event]
                         (callback (if (.-matches event) "dark" "light"))))))

(defn trace-error-handler
  [app-error-handler _dispatch]
  (fn [e]
    (rfx/log-and-continue-error-handler e)
    (app-error-handler e)))

(defn ensure-rfx-dev! []
  (let [body     (.-body js/document)
        existing (.getElementById js/document "rfx-dev")]
    (when (nil? existing)
      (let [div (.createElement js/document "div")]
        (set! (.-id div) "rfx-dev")
        (.appendChild body div)))))

(defonce root
  (delay (createRoot (.getElementById js/document "rfx-dev"))))

(defn init!
  [app-context]

  (ensure-rfx-dev!)

  (let [dispatch (:dispatch db/context)]
    (watch-system-theme #(dispatch [::db/set-ui-theme %])))

  (.render @root
           (hsx/create-element
             [:> db/AppContextProvider #js {"value" app-context}
              [:> rfx/RfxContextProvider #js {"value" db/context}
               [:<>
                [views/rfx-icon]
                [views/rfx-slide]]]])))

(defn wrap-dev
  [app-context]
  (let [dev-dispatch (:dispatch db/context)
        use-dev-sub  (:use-sub db/context)]
    (init! app-context)
    (let [next-ctx      (-> app-context
                            (update :store dev.store/trace-store dev-dispatch use-dev-sub)
                            (update :error-handler trace-error-handler dev-dispatch))
          handler       (rfx/handler rfx/global-registry (:store next-ctx) (:error-handler next-ctx))
          queue         (dev.queue/trace-queue (stable-queue/event-queue handler (:error-handler next-ctx))
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
