(defproject io.factorhouse/rfx "2.0.0"
  :description "An implementation of re-frame built for modern React"
  :url "http://github.com/factorhouse/rfx"
  :license {:name         "Apache-2.0 License"
            :url          "https://www.apache.org/licenses/LICENSE-2.0"
            :distribution :repo
            :comments     "same as Kafka"}
  :dependencies [[org.clojure/clojure "1.12.0" :scope "provided"]
                 [org.clojure/tools.logging "1.3.0"]]
  :source-paths ["modules/rfx/src"
                 "modules/re-frame-bridge/src"
                 "modules/rfx-dev/src"
                 "modules/rfx-demo/src"]
  :profiles {:dev {:dependencies [[thheller/shadow-cljs "2.28.20"]
                                  [com.stuartsierra/dependency "1.0.0"]
                                  [io.factorhouse/hsx "1.0.11"]
                                  [org.babashka/sci "0.9.44"]]}}
  :repl-options {:init-ns io.factorhouse.rfx.core})
