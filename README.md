# Rfx

Rfx is a modern, API-compatible drop-in replacement for [re-frame](https://github.com/day8/re-frame), designed for use with React 18+ and a pluggable storage backend.

It integrates seamlessly with vanilla React and popular libraries like [uix](https://github.com/pitch-io/uix) or even [Reagent](https://github.com/reagent-project/reagent), giving you the flexibility to adopt modern React standards while leveraging familiar patterns from re-frame.

## Why choose Rfx?

React 18+ introduced significant updates to React's rendering pipeline, including Concurrent Rendering and Suspense, which are incompatible with Reagent (details).

### The problem

* **Reagent's Compatibility**: Reagent is stuck on React 17, which is now over 4 years old.
* **React Ecosystem**: Much of the modern React ecosystem (libraries, tools, patterns) requires React 18+.
* **Forward Momentum**: React 18+ is the standard for new development, and staying on React 17 creates long-term maintenance challenges.

### The solution

Rfx provides a clear path forward for codebases stuck on Reagent:

* Retain the **re-frame API** and familiar event-driven architecture.
* Adopt **modern React standards**, unlocking access to the full React ecosystem.
* Support a **pluggable storage backend**, with zustand as the default option.

This library is part of our ongoing efforts at Factor House to migrate large ClojureScript codebases to modern React without sacrificing the core principles and patterns that make re-frame powerful.

### Features

* **API Compatibility**: Drop-in replacement for re-frame—no need to rewrite your event handlers or subscriptions.
* **Modern React Support**: Fully compatible with React 18+, including features like Concurrent Rendering.
* **Performance**: rfx is written in <150 lines of Clojure, does not depend on RAtom abstractions.
* **Flexible Storage**: Pluggable backend system for state management, with zustand as the default backend.
* **Framework Agnostic**: Works with vanilla React, uix, and even Reagent (though migrating from Reagent is recommended).
* **Future-Proof**: Stay aligned with React's ongoing development and ecosystem advancements.

## Getting started

### Instalation

Add Rfx to your project:

```clojure 
;; deps.edn
{:deps {io.factorhouse/rfx {:mvn/version "1.0.0"}}}
```

### Basic usage

```clojure 
(ns my-app.core
  (:require [io.factorhouse.rfx.core :as rf])) ;; <-- has the same API as re-frame.core

;; Example event registration
(uf/reg-event-db
 :initialize
 (fn [_ _]
   {:counter 0}))

(uf/reg-event-db 
  :counter/increment
  (fn [db _]
    (update db :counter inc)))

;; Example subscription
(uf/reg-sub
 :counter
 (fn [db _]
   (:counter db)))

;; Using subscriptions with your library of choice:

;; 1) React (functional component)
;; (:require ["react" :as react])
(defn test-ui-react []
  (let [counter (rf/use-sub [:counter])]
    (react/createElement
      "div" #js {:onClick #(rf/dispatch [:counter/increment])}
      (str "The value of the counter is " counter))))

;; 2) HSX
;; (:require [io.factorhouse.hsx.core :as hsx])
(defn test-ui-hsx []
  (let [counter (rf/use-sub [:counter])]
    [:div {:on-click #(rf/dispatch [:counter/increment])}
     "The value of counter is " counter]))

;; 3) Uix
;; (:require [uix.core :refer [defui $]])
(defui button [] 
       (let [counter (rf/use-sub [:counter])]
         ($ :div {:on-click #(rf/dispatch [:counter/increment])}
          "The value of counter is " counter)))

;; 4) Reagent (not recommended, but it works)
(defn test-ui-reagent* []
  (let [counter (rf/use-sub [:counter])]
    [:div {:on-click #(rf/dispatch [:counter/increment])}
     "The value of counter is " counter]))

(defn test-ui-reagent []
  ;; Will need to use :f> functional components with Reagent
  [:f> test-ui-reagent*])
```

## Learning

We suggest reading the [official re-frame docs](https://github.com/day8/re-frame/tree/master/docs).

## Differences from re-frame

### use-sub vs subscribe

In rfx, there is no need to dereference a subscription inside your component as subscriptions use React hooks (as opposed to RAtoms with re-frame). 

rfx provides a `io.factorhouse.rfx.core/subscribe` function with the same API as `re-frame.core/subscribe` to assist large codebases in migrating to rfx. The subscribe function simply wraps the rfx subscription hook in a [delay](https://clojuredocs.org/clojure.core/delay).

```clojure 
(defn test-ui []
  (let [counter (uf/subscribe [:counter])]
    [:div @counter])) ;; supported, but we prefer use-sub 
```

We suggest using `io.factorhouse.rfx.core/use-sub`.

## License

Copyright © 2025 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
