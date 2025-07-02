(ns re-frame.core
  "A compatibility namespace aliasing io.factorhouse.rfx.core/* as re-frame.core/*

  Intended to assist with migrations of large codebases off of Reagent

  Shout outs to re-frame: https://github.com/day8/re-frame"
  (:refer-clojure :exclude [error-handler])
  (:require [io.factorhouse.rfx.core :as rfx]
            [io.factorhouse.rfx.store :as store]))

;; -- Re-frame API over Rfx

(defonce app-db
  (:store rfx/global-context))

(defn reg-sub
  ([sub-id]
   (rfx/reg-sub sub-id))
  ([sub-id & args]
   ;; TODO: add better validation
   (let [signals (take-nth 2 (butlast (rest args)))
         sub-f   (last args)]
     (rfx/reg-sub sub-id signals sub-f))))

(def reg-event-db rfx/reg-event-db)
(def reg-event-fx rfx/reg-event-fx)
(defn reg-fx [fx-id fx-fn]
  (rfx/reg-fx fx-id (fn [_ val]
                      (fx-fn val))))
(def reg-cofx rfx/reg-cofx)
(def inject-cofx rfx/inject-cofx)

(defn dispatch
  [event]
  (rfx/dispatch rfx/global-context event))

#?(:cljs
   (defn subscribe
     [sub]
     (let [s (store/use-sub app-db sub)]
       (delay s))))

#?(:clj
   (defn subscribe
     [sub]
     (reify clojure.lang.IDeref
       (deref [_] (store/subscribe app-db sub)))))

(defn make-restore-fn []
  (let [prev-state (store/snapshot app-db)]
    (fn []
      (store/next-state! app-db prev-state))))

(defn clear-subscription-cache! []
  (rfx/clear-subscription-cache! rfx/global-context))

#?(:cljs
   (rfx/reg-fx
    :fx
    (fn [context seq-of-effects]
      (if-not (sequential? seq-of-effects)
        (throw (ex-info (str "\":fx\" effect expects a seq, but was given " (type seq-of-effects))
                        {:input seq-of-effects}))
        (doseq [[effect-key effect-value] (remove nil? seq-of-effects)]
          (if (= :dispatch effect-key)
            (rfx/dispatch context effect-value)
            (if-let [effect-fn (get-in @(:registry context) [:fx effect-key] false)]
              (effect-fn context effect-value)
              (throw (ex-info (str effect-key "in \":fx\" has no associated handler.")
                              {:input seq-of-effects})))))))))
