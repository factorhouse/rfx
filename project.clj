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
  :profiles {:smoke {:pedantic? :abort}
             :dev   {:dependencies [[thheller/shadow-cljs "2.28.20"]
                                    [io.factorhouse/hsx "1.0.11"]
                                    [org.babashka/sci "0.9.44"]
                                    [clj-kondo "2025.01.16"]
                                    [datascript "1.7.4"]]
                     :plugins      [[dev.weavejester/lein-cljfmt "0.13.0"]]
                     :source-paths ["examples/datascript/src"
                                    "examples/re-frame-bridge-todomvc/src"
                                    "examples/rfx-todomvc/src"]}}
  :aliases {"fmt"    ["with-profile" "+smoke" "cljfmt" "check"]
            "fmtfix" ["with-profile" "+smoke" "cljfmt" "fix"]}
  :repl-options {:init-ns io.factorhouse.rfx.core})