(ns io.factorhouse.rfx.core
  "An implementation of re-frame built for modern React"
  (:require [io.factorhouse.rfx.loggers :as loggers]
            [io.factorhouse.rfx.store :as store]
            #?(:cljs [goog.async.nextTick])
            #?(:clj [io.factorhouse.rfx.stores.atom])
            #?(:cljs [io.factorhouse.rfx.stores.zustand :as stores.zustand])))

(def empty-queue
  #?(:cljs #queue []
     :clj clojure.lang.PersistentQueue/EMPTY))

(def next-tick
  #?(:cljs goog.async.nextTick
     :clj identity))

(declare handle)

;; -- Router Loop ------------------------------------------------------------
;;
;; A call to "re-frame.core/dispatch" places an event on a queue for processing.
;; A short time later, the handler registered to handle this event will be run.
;; What follows is the implementation of this process.
;;
;; The task is to process queued events in a perpetual loop, one after
;; the other, FIFO, calling the registered event-handler for each, being idle when
;; there are no events, and firing up when one arrives.
;;
;; But browsers only have a single thread of control and we must be
;; careful to not hog the CPU. When processing events one after another, we
;; must regularly hand back control to the browser, so it can redraw, process
;; websockets, etc. But not too regularly! If we are in a de-focused browser
;; tab, our app will be CPU throttled. Each time we get back control, we have
;; to process all queued events, or else something like a bursty websocket
;; (producing events) might overwhelm the queue. So there's a balance.
;;
;; The processing/handling of an event happens "asynchronously" sometime after
;; that event was enqueued via "dispatch". The original implementation of this router loop
;; used `core.async`. As a result, it was fairly simple, and it mostly worked,
;; but it did not give enough control. So now we hand-roll our own,
;; finite-state-machine and all.
;;
;; In what follows, the strategy is this:
;;   - maintain a FIFO queue of `dispatched` events.
;;   - when a new event arrives, "schedule" processing of this queue using
;;     goog.async.nextTick, which means it will happen "very soon".
;;   - when processing events, one after the other, do ALL the currently
;;     queued events. Don't stop. Don't yield to the browser. Hog that CPU.
;;   - but if any new events are dispatched during this cycle of processing,
;;     don't do them immediately. Leave them queued. Yield first to the browser,
;;     and do these new events in the next processing cycle. That way we drain
;;     the queue up to a point, but we never hog the CPU forever. In
;;     particular, we handle the case where handling one event will beget
;;     another event. The freshly begotten event will be handled next cycle,
;;     with yielding in-between.
;;   - In some cases, an event should not be handled until after the GUI has been
;;     updated, i.e., after the next Reagent animation frame. In such a case,
;;     the event should be dispatched with :flush-dom metadata like this:
;;       (dispatch ^:flush-dom [:event-id other params])
;;     Such an event will temporarily block all further processing because
;;     events are processed sequentially: we handle one event completely
;;     before we handle the ones behind it.
;;
;; Implementation notes:
;;   - queue processing can be in a number of states: scheduled, running, paused
;;     etc. So it is modeled as a Finite State Machine.
;;     See "-fsm-trigger" (below) for the states and transitions.
;;   - the scheduling is done via "goog.async.nextTick" which is pretty quick
;;   - when the event has :flush-dom metadata we schedule via
;;       "reagent.core.after-render"
;;     which will run event processing after the next Reagent animation frame.
;;

;; Events can have metadata which says to pause event processing.
;; event metadata -> "run later" functions
(def later-fns
  {:flush-dom next-tick  ;; one tick after the end of the next animation frame
   :yield     next-tick}) ;; almost immediately

;; Event Queue Abstraction
(defprotocol IEventQueue

  ;; -- API
  (push [this event])
  (add-post-event-callback [this id callback-fn])
  (remove-post-event-callback [this id])
  (purge [this])

  ;; -- Implementation via a Finite State Machine
  (-fsm-trigger [this trigger arg])

  ;; -- Finite State Machine actions
  (-add-event [this event])
  (-process-1st-event-in-queue [this])
  (-run-next-tick [this])
  (-run-queue [this])
  (-exception [this ex])
  (-pause [this later-fn])
  (-resume [this])
  (-call-post-event-callbacks [this event]))

;; Concrete implementation of IEventQueue
(deftype EventQueue [#?(:cljs ^:mutable fsm-state               :clj ^:volatile-mutable fsm-state)
                     #?(:cljs ^:mutable queue                   :clj ^:volatile-mutable queue)
                     #?(:cljs ^:mutable post-event-callback-fns :clj ^:volatile-mutable post-event-callback-fns)]
  IEventQueue

  ;; -- API ------------------------------------------------------------------

  (push [this event]         ;; presumably called by dispatch
    (-fsm-trigger this :add-event event))

  ;; register a callback function which will be called after each event is processed
  (add-post-event-callback [_ id callback-fn]
    (if (contains? post-event-callback-fns id)
      (loggers/warn "re-frame: overwriting existing post event call back with id:" id))
    (->> (assoc post-event-callback-fns id callback-fn)
         (set! post-event-callback-fns)))

  (remove-post-event-callback [_ id]
    (if-not (contains? post-event-callback-fns id)
      (loggers/warn :warn "re-frame: could not remove post event call back with id:" id)
      (->> (dissoc post-event-callback-fns id)
           (set! post-event-callback-fns))))

  (purge [_]
    (set! queue empty-queue))

  ;; -- FSM Implementation ---------------------------------------------------

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
              [:idle :add-event] [:scheduled #(do (-add-event this arg)
                                                  (-run-next-tick this))]

              ;; State: :scheduled  (the queue is scheduled to run, soon)
              [:scheduled :add-event] [:scheduled #(-add-event this arg)]
              [:scheduled :run-queue] [:running #(-run-queue this)]

              ;; State: :running (the queue is being processed one event after another)
              [:running :add-event] [:running #(-add-event this arg)]
              [:running :pause] [:paused #(-pause this arg)]
              [:running :exception] [:idle #(-exception this arg)]
              [:running :finish-run] (if (empty? queue)     ;; FSM guard
                                       [:idle]
                                       [:scheduled #(-run-next-tick this)])

              ;; State: :paused (:flush-dom metadata on an event has caused a temporary pause in processing)
              [:paused :add-event] [:paused #(-add-event this arg)]
              [:paused :resume] [:running #(-resume this)]

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
        (handle event-v)
        (set! queue (pop queue))
        (-call-post-event-callbacks this event-v)
        (catch #?(:cljs :default :clj Exception) ex
          (-fsm-trigger this :exception ex)))))

  (-run-next-tick
    [this]
    (next-tick #(-fsm-trigger this :run-queue nil)))

  ;; Process all the events currently in the queue, but not any new ones.
  ;; Be aware that events might have metadata which will pause processing.
  (-run-queue
    [this]
    (loop [n (count queue)]
      (if (zero? n)
        (-fsm-trigger this :finish-run nil)
        (if-let [later-fn (some later-fns (-> queue peek meta keys))]  ;; any metadata which causes pausing?
          (-fsm-trigger this :pause later-fn)
          (do (-process-1st-event-in-queue this)
              (recur (dec n)))))))

  (-exception
    [this ex]
    (purge this)   ;; purge the queue
    (throw ex))

  (-pause
    [this later-fn]
    (later-fn #(-fsm-trigger this :resume nil)))

  (-call-post-event-callbacks
    [_ event-v]
    (doseq [callback (vals post-event-callback-fns)]
      (callback event-v queue)))

  (-resume
    [this]
    (-process-1st-event-in-queue this)  ;; do the event which paused processing
    (-run-queue this)))                 ;; do the rest of the queued events

;; ---------------------------------------------------------------------------
;; Event Queue
;; When "dispatch" is called, the event is added into this event queue.  Later,
;;  the queue will "run" and the event will be "handled" by the registered function.
;;
(def event-queue (->EventQueue :idle empty-queue {}))

(defn dispatch
  [event]
  (if (nil? event)
    (throw (ex-info "re-frame: you called \"dispatch\" without an event vector." {}))
    (push event-queue event))
  nil)

(defonce app-db
  #?(:cljs (stores.zustand/store {})
     :clj  (atom {})))

(defonce ^:private registry
  (atom {}))

(defn reg-cofx
  [cofx-id cofx-fn]
  (swap! registry assoc-in [:cofx cofx-id] cofx-fn))

(defn inject-cofx
  ([id]
   {:id id :value nil})
  ([id value]
   {:id id :value value}))

(defn handle
  [[event-id & _args :as event]]
  (let [curr-registry @registry]
    (if-let [{:keys [event-f coeffects]} (get-in curr-registry [:event event-id])]
      (let [curr-state (store/snapshot-state app-db)
            ctx        (reduce
                         (fn [ctx {:keys [id value]}]
                           (if-let [cofx-fn (get-in curr-registry [:cofx id])]
                             (cofx-fn ctx value)
                             (do
                               (loggers/warn "No such cofx named " (pr-str id) ". Returning previous context.")
                               ctx)))
                         {:db curr-state}
                         coeffects)
            result     (event-f ctx event)]

        (when-let [next-db (:db result)]
          (let [db-fn (get-in curr-registry [:fx :db])]
            (db-fn next-db)))

        (doseq [[fx-id fx-val] (dissoc result :db)]
          (if-let [fx-fn (get-in curr-registry [:fx fx-id])]
            (fx-fn fx-val)
            (loggers/warn "Cannot find fx named " (pr-str fx-id)))))
      (loggers/warn "Cannot find event named " (pr-str event-id) "."))))

(defn reg-sub
  ([sub-id]
   (let [sub {:sub-f (fn [db _] db) :signals []}]
     (swap! registry assoc-in [:sub sub-id] sub)))
  ([sub-id signals sub-f]
   (let [sub     {:sub-f sub-f :signals signals}]
     (swap! registry assoc-in [:sub sub-id] sub))))

(defn- subscribe*
  [curr-registry [sub-id & _sub-args :as sub] db]
  (if-let [{:keys [sub-f signals]} (get-in curr-registry [:sub sub-id])]
    (let [db-input (if (seq signals)
                     (if (= 1 (count signals))
                       (subscribe* curr-registry (first signals) db)
                       (into [] (map #(subscribe* curr-registry % db)) signals))
                     db)]
      (sub-f db-input sub))
    (loggers/warn "Cannot find subscription named " (pr-str sub-id) ".")))

(defn subscribe-outside-of-react-context
  [sub]
  (subscribe* @registry sub (store/snapshot-state app-db)))

#?(:cljs
   (defn subscribe
     "Deprecated: used to offer compatibility with io.factorhouse.rfx.core/subscribe.

     Use io.factorhouse.rfx.core/use-sub instead"
     [sub]
     (let [s (store/use-store app-db (partial subscribe* @registry sub))]
       (delay s))))

#?(:clj
   (defn subscribe
     [sub]
     (reify clojure.lang.IDeref
       (deref [_]
         (store/use-store app-db (partial subscribe* @registry sub))))))

(reg-cofx :subscription
  (fn [coeffects [sub-id & _sub-args :as sub]]
    (assoc coeffects sub-id (subscribe-outside-of-react-context sub))))

(defn use-sub
  [sub]
  (store/use-store app-db (partial subscribe* @registry sub)))

(defn reg-fx
  [fx-id f]
  (swap! registry assoc-in [:fx fx-id] f))

(reg-fx :db
  (fn db-fx [next-value]
    (store/snapshot-reset! app-db next-value)))

(reg-fx :dispatch-n
  (fn dispatch-n-fx [dispatch-events]
    (doseq [event dispatch-events]
      (dispatch event))))

(reg-fx :dispatch
  (fn dispatch-fx [event]
    (dispatch event)))

(defn reg-event-fx
  ([event-fx-id f]
   (let [fx {:event-f f}]
     (swap! registry assoc-in [:event event-fx-id] fx)))
  ([event-fx-id coeffects f]
   (let [fx {:event-f f :coeffects coeffects}]
     (swap! registry assoc-in [:event event-fx-id] fx))))

(defn reg-event-db
  [event-id event-f]
  (let [event {:event-f (fn [{:keys [db]} event] {:db (event-f db event)})}]
    (swap! registry assoc-in [:event event-id] event)))

(defn make-restore-fn []
  (let [prev-state (store/snapshot-state app-db)]
    (fn []
      (store/snapshot-reset! app-db prev-state))))

(defn clear-subscription-cache! []
  (swap! registry dissoc :sub))
