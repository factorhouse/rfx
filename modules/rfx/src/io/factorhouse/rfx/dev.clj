(ns io.factorhouse.rfx.dev)

(def rfx-dev-js-cdn "https://factorhouse.io/xxx.js")
(def rfx-dev-css-cdn "https://factorhouse.io/xxx.css")

(defn rfx-dev-script-tag []
  [:script {:src rfx-dev-js-cdn}])

(defn rfx-dev-css-tag []
  [:script {:src rfx-dev-css-cdn}])
