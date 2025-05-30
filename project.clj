(defproject io.factorhouse/rfx "0.1.14"
  :description "An implementation of re-frame built for modern React"
  :url "http://github.com/factorhouse/rfx"
  :license {:name         "Apache-2.0 License"
            :url          "https://www.apache.org/licenses/LICENSE-2.0"
            :distribution :repo
            :comments     "same as Kafka"}
  :dependencies [[org.clojure/clojure "1.12.0" :scope "provided"]
                 [org.clojure/tools.logging "1.3.0" :scope "provided"]]
  :source-paths ["modules/rfx/src"
                 "modules/re-frame-bridge/src"
                 "modules/rfx-dev/src"]
  :profiles {:smoke {:pedantic? :abort}
             :dev   {:dependencies [[thheller/shadow-cljs "3.0.6" :exclusions [nrepl]]
                                    [io.factorhouse/hsx "0.1.23"]
                                    [org.babashka/sci "0.9.44"]
                                    [clj-kondo "2025.01.16" :exclusions [com.cognitect/transit-java javax.xml.bind/jaxb-api com.cognitect/transit-clj]]
                                    [datascript "1.7.4"]
                                    [com.pitch/uix.core "1.4.3"]
                                    [com.pitch/uix.dom "1.4.3"]]
                     :plugins      [[dev.weavejester/lein-cljfmt "0.13.0"]]
                     :source-paths ["examples/datascript/src"
                                    "examples/re-frame-bridge-todomvc/src"
                                    "examples/rfx-todomvc/src"
                                    "examples/uix/src"]}}
  :aliases {"kondo"  ["with-profile" "+smoke" "run" "-m" "clj-kondo.main" "--lint" "modules/rfx" "modules/re-frame-bridge"]
            "fmt"    ["with-profile" "+smoke" "cljfmt" "check"]
            "fmtfix" ["with-profile" "+smoke" "cljfmt" "fix"]}
  :repositories [["github" {:url      "https://maven.pkg.github.com/factorhouse/rfx"
                            :username "private-token"
                            :password :env/GITHUB_TOKEN}]
                 ["github-hsx" {:url      "https://maven.pkg.github.com/factorhouse/hsx"
                                :username "private-token"
                                :password :env/GITHUB_TOKEN}]]
  :repl-options {:init-ns io.factorhouse.rfx.core})
