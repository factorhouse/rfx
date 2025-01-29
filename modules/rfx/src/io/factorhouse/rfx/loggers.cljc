(ns io.factorhouse.rfx.loggers
  (:require #?@(:clj [[clojure.tools.logging :as log]
                      [clojure.string :as str]])))

#?(:clj
   (defn log [level & args]
     (log/log level (if (= 1 (count args))
                      (first args)
                      (str/join " " args)))))

(defn error
  [& args]
  #?(:cljs (apply js/console.error args)
     :clj  (apply log :error args)))

(defn warn
  [& args]
  #?(:cljs (apply js/console.warn args)
     :clj  (apply log :warn args)))

(defn info
  [& args]
  #?(:cljs (apply js/console.info args)
     :clj  (apply log :info args)))
