(ns io.factorhouse.rfx.store)

(defprotocol IStore
  "Protocol for interacting with a shared state store, supporting both React component access
   and external access methods."
  (subscribe [this subscription]
    "Returns the current value of the subscription. Intended to be called outside a React context.")
  (use-sub [this subscription]
    "Returns a React hook to access the subscription within a React component.
     Usage: `(let [state (use-sub store [:subscription-id] ...)`

     The returned hook allows components to reactively subscribe to changes in the store.")
  (snapshot-reset! [_ newval]
    "Resets the store to newval without regard for the current value.")
  (snapshot-state [this]
    "Returns the current internal state of the store for inspection or debugging purposes."))
