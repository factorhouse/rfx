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
                 "modules/rfx-dev/src"]
  :profiles {:dev {:dependencies [[thheller/shadow-cljs "2.28.20"]
                                  [io.factorhouse/hsx "1.0.11"]
                                  [org.babashka/sci "0.9.44"]
                                  [clj-kondo "2025.01.16"]
                                  [datascript "1.7.4"]]
                   :source-paths ["examples/datascript/src"
                                  "examples/re-frame-bridge-todomvc/src"
                                  "examples/rfx-todomvc/src"]}}
  :repl-options {:init-ns io.factorhouse.rfx.core})
