(ns io.factorhouse.rfx.queues.stable
  "This is the 'stable' implementation of an EventQueue.

  Original implementation comes from re-frame: https://github.com/day8/re-frame/blob/master/src/re_frame/router.cljc"
  (:require [io.factorhouse.rfx.queue :as queue]
            #?(:cljs [goog.async.nextTick])))

(def empty-queue
  #?(:cljs #queue []
     :clj  clojure.lang.PersistentQueue/EMPTY))

(def next-tick
  #?(:cljs goog.async.nextTick
     :clj  identity))

(def later-fns
  {:flush-dom next-tick                                     ;; one tick after the end of the next animation frame
   :yield     next-tick})                                   ;; almost immediately

;; Concrete implementation of IEventQueue
(deftype EventQueue [#?(:cljs ^:mutable fsm-state :clj ^:volatile-mutable fsm-state)
                     #?(:cljs ^:mutable queue :clj ^:volatile-mutable queue)
                     #?(:cljs ^:mutable post-event-callback-fns :clj ^:volatile-mutable post-event-callback-fns)
                     handler
                     error-handler]
  queue/IEventQueue

  ;; -- API ------------------------------------------------------------------

  (push [this event]                                        ;; presumably called by dispatch
    (queue/-fsm-trigger this :add-event event))

  ;; register a callback function which will be called after each event is processed
  (add-post-event-callback [_ id callback-fn]
    (if (contains? post-event-callback-fns id)
      (error-handler {:errors [{:level   :info
                                :message (str "rfx: overwriting existing post event call back with id:" id)}]}))
    (->> (assoc post-event-callback-fns id callback-fn)
         (set! post-event-callback-fns)))

  (remove-post-event-callback [_ id]
    (if-not (contains? post-event-callback-fns id)
      (error-handler {:errors [{:level   :warn
                                :message (str "rfx: could not remove post event call back with id:" id)}]})
      (->> (dissoc post-event-callback-fns id)
           (set! post-event-callback-fns))))

  (purge [_]
    (set! queue empty-queue))

  ;; -- FSM Implementation ---------------------------------------------------
  queue/IFiniteStateMachine
  (-fsm-trigger
    [this trigger arg]

    ;; The following "case" implements the Finite State Machine.
    ;; Given a "trigger", and the existing FSM state, it computes the
    ;; new FSM state and the transition action (function).

    (locking this
      (let [[new-fsm-state action-fn]
            (case [fsm-state trigger]

              ;; You should read the following "case" as:
              ;; [current-FSM-state trigger] -> [new-FSM-state action-fn]
              ;;
              ;; So, for example, the next line should be interpreted as:
              ;; if you are in state ":idle" and a trigger ":add-event"
              ;; happens, then move the FSM to state ":scheduled" and execute
              ;; that two-part "do" function.
              [:idle :add-event] [:scheduled #(do (queue/-add-event this arg)
                                                  (queue/-run-next-tick this))]

              ;; State: :scheduled  (the queue is scheduled to run, soon)
              [:scheduled :add-event] [:scheduled #(queue/-add-event this arg)]
              [:scheduled :run-queue] [:running #(queue/-run-queue this)]

              ;; State: :running (the queue is being processed one event after another)
              [:running :add-event] [:running #(queue/-add-event this arg)]
              [:running :pause] [:paused #(queue/-pause this arg)]
              [:running :exception] [:idle #(queue/-exception this arg)]
              [:running :finish-run] (if (empty? queue)     ;; FSM guard
                                       [:idle]
                                       [:scheduled #(queue/-run-next-tick this)])

              ;; State: :paused (:flush-dom metadata on an event has caused a temporary pause in processing)
              [:paused :add-event] [:paused #(queue/-add-event this arg)]
              [:paused :resume] [:running #(queue/-resume this)]

              (throw (ex-info (str "re-frame: router state transition not found. " fsm-state " " trigger)
                              {:fsm-state fsm-state, :trigger trigger})))]

        ;; The "case" above computed both the new FSM state, and the action. Now, make it happen.
        (set! fsm-state new-fsm-state)
        (when action-fn (action-fn)))))

  (-add-event
    [_ event]
    (set! queue (conj queue event)))

  (-process-1st-event-in-queue
    [this]
    (let [event-v (peek queue)]
      (try
        (handler this event-v)
        (set! queue (pop queue))
        (queue/-call-post-event-callbacks this event-v)
        (catch #?(:cljs :default :clj Exception) ex
          (queue/-fsm-trigger this :exception ex)))))

  (-run-next-tick
    [this]
    (next-tick #(queue/-fsm-trigger this :run-queue nil)))

  ;; Process all the events currently in the queue, but not any new ones.
  ;; Be aware that events might have metadata which will pause processing.
  (-run-queue
    [this]
    (loop [n (count queue)]
      (if (zero? n)
        (queue/-fsm-trigger this :finish-run nil)
        (if-let [later-fn (some later-fns (-> queue peek meta keys))] ;; any metadata which causes pausing?
          (queue/-fsm-trigger this :pause later-fn)
          (do (queue/-process-1st-event-in-queue this)
              (recur (dec n)))))))

  (-exception
    [this ex]
    (queue/purge this)                                      ;; purge the queue
    (throw ex))

  (-pause
    [this later-fn]
    (later-fn #(queue/-fsm-trigger this :resume nil)))

  (-call-post-event-callbacks
    [_ event-v]
    (doseq [callback (vals post-event-callback-fns)]
      (callback event-v queue)))

  (-resume
    [this]
    (queue/-process-1st-event-in-queue this)                ;; do the event which paused processing
    (queue/-run-queue this)))                               ;; do the rest of the queued events

(defn event-queue
  [handler error-handler]
  (EventQueue. :idle empty-queue {} handler error-handler))
