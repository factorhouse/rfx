(ns re-frame.sub-test
  "Adapted from: https://github.com/day8/re-frame/blob/master/test/re_frame/subs_test.cljs"
  (:require [re-frame.core :as rf]
            [clojure.test :refer :all]))

(defn fixture-re-frame
  [f]
  (let [restore-re-frame (rf/make-restore-fn)]
    (f)
    (restore-re-frame)))

(use-fixtures :each fixture-re-frame)

(deftest test-reg-sub-clj-repl
  (rf/reg-sub
    :a-sub
    (fn [db _] (:a db)))

  (rf/reg-sub
    :b-sub
    (fn [db _] (:b db)))

  (rf/reg-sub
    :a-b-sub
    :<- [:a-sub]
    :<- [:b-sub]
    (fn [[a b] _]
      {:a a :b b}))

  (let [test-sub (rf/subscribe [:a-b-sub])]
    (reset! (rf/app-db) {:a 1 :b 2})
    (is (= {:a 1 :b 2} @test-sub))
    (reset! (rf/app-db) {:a 1 :b 3})
    (is (= {:a 1 :b 3} @test-sub))))
