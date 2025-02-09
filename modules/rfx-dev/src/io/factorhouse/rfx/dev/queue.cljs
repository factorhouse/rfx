(ns io.factorhouse.rfx.dev.queue
  (:require [io.factorhouse.rfx.dev.db :as db]
            [io.factorhouse.rfx.dev.util :as util]
            [io.factorhouse.rfx.queue :as queue]))

(defn trace-queue
  [queue dev-dispatch]
  (reify queue/IEventQueue
    (push
      [_ event]
      (dev-dispatch [::db/mark-event {:type  :push
                                      :event event
                                      :ts    (util/now)}])
      (queue/push queue event))

    (add-post-event-callback
      [_ id callback-fn]
      (dev-dispatch [::db/mark-event {:type        :add-post-event-callback
                                      :id          id
                                      :ts          (util/now)
                                      :callback-fn callback-fn}])
      (queue/add-post-event-callback queue id callback-fn))

    (remove-post-event-callback
      [_ id]
      (dev-dispatch [::db/mark-event {:type :remove-post-event-callback
                                      :id   id
                                      :ts   (util/now)}])
      (queue/remove-post-event-callback queue id))

    (purge [_]
      (dev-dispatch [::db/mark-event {:type :purge
                                      :ts   (util/now)}])
      (queue/purge queue))))
