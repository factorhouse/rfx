(ns io.factorhouse.rfx.interceptor
  (:require [clojure.set :as set]))

(def debug-enabled? true)

(def mandatory-interceptor-keys #{:id :after :before})

(def optional-interceptor-keys #{:comment})

(defn interceptor?
  [m]
  (and (map? m)
       (= mandatory-interceptor-keys
          (-> m keys set (set/difference optional-interceptor-keys)))))

(defn ->interceptor
  [& {:as m :keys [id comment before after]}]
  (when debug-enabled?
    (if-let [unknown-keys (seq (set/difference
                                (-> m keys set)
                                mandatory-interceptor-keys
                                optional-interceptor-keys))]
      (throw (ex-info (str "re-frame: ->interceptor" m "has unknown keys:" unknown-keys) {}))))
  (cond-> {:id     (or id :unnamed)
           :before before
           :after  after}
    comment (assoc :comment comment)))