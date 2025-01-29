(defproject io.factorhouse/rfx "1.0.6"
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
                 "modules/rfx-zustand/src"
                 "modules/rfx-dev/src"
                 "modules/rfx-demo/src"]
  :profiles {:dev {:dependencies [[thheller/shadow-cljs "2.28.20"]]}}
  :repl-options {:init-ns io.factorhouse.rfx.core})
