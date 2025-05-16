(ns io.factorhouse.rfx.queue)

;; Event Queue Abstraction
(defprotocol IEventQueue
  ;; -- API
  (push [this event])
  (add-post-event-callback [this id callback-fn])
  (remove-post-event-callback [this id])
  (purge [this]))

(defprotocol IFiniteStateMachine
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
