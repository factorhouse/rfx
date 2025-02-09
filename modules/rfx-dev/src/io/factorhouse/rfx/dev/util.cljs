(ns io.factorhouse.rfx.dev.util
  (:require [clojure.string :as str]
            [goog.object :as obj]
            ["dayjs" :as dayjs]
            ["dayjs/plugin/relativeTime" :as relativeTime]))

(.extend dayjs relativeTime)

;; Very hacky way to get the display name of the component we are inside
;;
;; * Requires React dev tools running
;; * Undocumented API, probably going to break at some point...
(defn component-display-name []
  (try
    (when-let [owner (some-> js/__REACT_DEVTOOLS_GLOBAL_HOOK__
                             (obj/get "renderers")
                             (seq)
                             (first)
                             (second)
                             (obj/get "getCurrentFiber"))]
      (let [owner (some-> (owner) .-type)]
        (or (.-displayName owner) (.-name owner))))
    (catch :default _)))

(defn class-names
  [& xs]
  (str/join " " xs))

(defn ago
  [ts]
  (.from (dayjs ts) (dayjs)))

(defn now []
  (js/Date.now))
