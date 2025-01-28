(ns io.factorhouse.rfx.store)

(defprotocol IStore
  "Protocol for interacting with a shared state store, supporting both React component access
   and external access methods."
  (use-store [this f]
    "Returns a React hook to access the store within a React component.
     Usage: `(let [state (use-store (fn [store] ...)] ...)`

     The returned hook allows components to reactively subscribe to changes in the store.")
  (snapshot-reset! [_ newval]
    "Resets the store to newval without regard for the current value. Must be called outside a React context.")
  (snapshot-state [this]
    "Returns the current internal state of the store for inspection or debugging purposes. Must be called outside a React context."))
