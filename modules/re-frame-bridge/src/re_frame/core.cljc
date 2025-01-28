(ns re-frame.core
  "A compatibility namespace aliasing io.factorhouse.rfx.core/* as re-frame.core/*

  Intended to assist with migrations of large codebases off of Reagent

  Shout outs to re-frame: https://github.com/day8/re-frame"
  (:require [io.factorhouse.rfx.core :as rf]))

(def app-db rf/app-db)

(defn reg-sub
  ([sub-id]
   (rf/reg-sub sub-id))
  ([sub-id & args]
   (let [signals (take-nth 2 (butlast (rest args)))
         sub-f   (last args)]
     (rf/reg-sub sub-id signals sub-f))))

(def reg-event-db rf/reg-event-db)
(def reg-event-fx rf/reg-event-fx)
(def reg-fx rf/reg-fx)
(def reg-cofx rf/reg-cofx)
(def inject-cofx rf/inject-cofx)
(def dispatch rf/dispatch)
(def subscribe rf/subscribe)
(def make-restore-fn rf/make-restore-fn)
(def clear-subscription-cache! rf/clear-subscription-cache!)
