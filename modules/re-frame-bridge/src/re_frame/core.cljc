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

(defn reg-sub-sugar
  [sub-id args]
  (let [[op f] (take-last 2 args)
        [input-args sub-f]
        (if (or
              ;; (reg-sub ::foo (fn [_ _]))
             (nil? f)
              ;; (reg-sub ::foo :<- [::bar] (fn [_ _]))) ;; input signal
             (vector? op))
          [(butlast (rest args)) (last args)]
          ;; (reg-sub ::foo ,,, :-> :foo)
          [(drop-last 2 args)
           (case op
             :-> (fn arrow-sub [signals _] (f signals))
             :=> (fn apply-sub [signals [_ & opts]] (apply f signals opts))
             (throw (ex-info (str "Expected `:->` or `:=>` as second-to-last argument, got: " op)
                             {:sub sub-id :input-args args})))])]
    [(take-nth 2 input-args) sub-f]))

(defn reg-sub
  ([sub-id]
   (rfx/reg-sub sub-id))
  ([sub-id & args]
   (let [[signals sub-f] (reg-sub-sugar sub-id args)]
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
