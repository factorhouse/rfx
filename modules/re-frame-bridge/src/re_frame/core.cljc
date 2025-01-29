(ns re-frame.core
  "A compatibility namespace aliasing io.factorhouse.rfx.core/* as re-frame.core/*

  Intended to assist with migrations of large codebases off of Reagent

  Shout outs to re-frame: https://github.com/day8/re-frame"
  (:require [io.factorhouse.rfx.core :as rfx]
            #?(:clj [io.factorhouse.rfx.stores.atom])
            #?(:cljs [io.factorhouse.rfx.stores.zustand :as zustand])))

(defn reg-sub
  ([sub-id]
   (rfx/reg-sub sub-id))
  ([sub-id & args]
   (let [signals (take-nth 2 (butlast (rest args)))
         sub-f   (last args)]
     (rfx/reg-sub sub-id signals sub-f))))

(def reg-event-db rfx/reg-event-db)
(def reg-event-fx rfx/reg-event-fx)
(def reg-fx rfx/reg-fx)
(def reg-cofx rfx/reg-cofx)
(def inject-cofx rfx/inject-cofx)
(def dispatch rfx/dispatch)

#?(:cljs (defn subscribe
           [sub]
           (let [s (rfx/use-sub sub)]
             (delay s))))

#?(:clj
   (defn subscribe
     [sub]
     (reify clojure.lang.IDeref
       (deref [_] (rfx/use-sub sub)))))

(def make-restore-fn rfx/make-restore-fn)
(def clear-subscription-cache! rfx/clear-subscription-cache!)

(def app-db rfx/app-db)

(rfx/init!
  {:store #?(:cljs (zustand/store {}) :clj (atom {}))})
