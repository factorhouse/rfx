(ns io.factorhouse.rfx.stores.atom
  (:require [io.factorhouse.rfx.store :as store]))

(extend-type #?(:cljs cljs.core.Atom :clj clojure.lang.IAtom)
  store/IStore
  (use-store [this f] (f @this))
  (snapshot-reset! [this newval] (reset! this newval))
  (snapshot-state [this] @this))
