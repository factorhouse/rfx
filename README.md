# Rfx

Rfx is a modern, API-compatible drop-in replacement for [re-frame](https://github.com/day8/re-frame), designed for use with React 18+ and a pluggable storage backend.

It integrates seamlessly with vanilla React, popular wrapper libraries like [uix](https://github.com/pitch-io/uix) or even [Reagent](https://github.com/reagent-project/reagent), giving you the flexibility to adopt modern React standards while leveraging familiar patterns from re-frame.

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

### Installation

Add Rfx to your project:

```clojure 
;; deps.edn
{:deps {io.factorhouse/rfx {:mvn/version "1.0.0"} ;; Core library
        io.factorhouse/rfx-zustand {:mvn/version "1.0.0"} ;; Bring in the pluggable store
        io.factorhouse/rfx-dev {:mvn/version "1.0.0"} ;; Useful DX tools
        }}
```

Optional: if you want a drop in replacement for `re-frame.core` use:

```clojure 
;; deps.edn
{:deps {io.factorhouse/re-frame-bridge {:mvn/version "1.0.0"}}}
```

### Basic usage

```clojure 
(ns my-app.core
  (:require [io.factorhouse.rfx.core :as rfx]
            [io.factorhouse.rfx.stores.zustand :as zustand]))

;; Declare our store
(defonce app-db (zustand/store {:number 0}))

;; Initialize Rfx
(rfx/init! {:store app-db})

;; Example event registration
(rfx/reg-event-db
 :initialize
 (fn [_ _]
   {:counter 0}))

(rfx/reg-event-db 
  :counter/increment
  (fn [db _]
    (update db :counter inc)))

;; Example subscription
(rfx/reg-sub
 :counter
 (fn [db _]
   (:counter db)))

;; Using subscriptions with your library of choice:

;; 1) React (functional component)
;; (:require ["react" :as react])
(defn test-ui-react []
  (let [counter (rfx/use-sub [:counter])]
    (react/createElement
      "div" #js {:onClick #(rfx/dispatch [:counter/increment])}
      (str "The value of the counter is " counter))))

;; 2) HSX
;; (:require [io.factorhouse.hsx.core :as hsx])
(defn test-ui-hsx []
  (let [counter (rfx/use-sub [:counter])]
    [:div {:on-click #(rfx/dispatch [:counter/increment])}
     "The value of counter is " counter]))

;; 3) Uix
;; (:require [uix.core :refer [defui $]])
(defui button [] 
       (let [counter (rfx/use-sub [:counter])]
         ($ :div {:on-click #(rf/dispatch [:counter/increment])}
          "The value of counter is " counter)))

;; 4) Reagent (not recommended, but it works)
(defn test-ui-reagent* []
  (let [counter (rfx/use-sub [:counter])]
    [:div {:on-click #(rfx/dispatch [:counter/increment])}
     "The value of counter is " counter]))

(defn test-ui-reagent []
  ;; Will need to use :f> functional components with Reagent
  [:f> test-ui-reagent*])
```

### Re-frame bridge

The `io.factorhouse/re-frame-bridge` dependency provides a shim `re-frame.core` namespace that taps into the rfx implementation. 

You can use this bridging library to assist with migrating existing re-frame codebases to Rfx.

See also:
* [hsx](https://github.com/factorhouse/hsx) - Factor House's Hiccup templating library for React
* [reagent-bridge](https://github.com/factorhouse/hsx) - similar to the Re-frame bridge: a shim `reagent.core` bridging library

### Rfx developer tools

Rfx comes with rich developer tools via the `io.factorhouse/rfx-dev` dependency.

Simply initialize Rfx as follows:

```clojure 
(ns my-app.core
  (:require [io.factorhouse.rfx.core :as rfx]
            [io.factorhouse.rfx.dev :as rfx-dev]
            [io.factorhouse.rfx.stores.zustand :as zustand]))

;; Initialize Rfx
(rfx/init!
  (rfx-dev/opts {:store (zustand/store {})}))
```

### Stores

Unique to Rfx is the ability to configure the storage backend. There are two storage backends: 

* `io.factorhouse.rfx.stores.zustand` - the default storage backend 
* `io.factorhouse.rfx.stores.atom` - wraps a Clojure atom, only intended for debugging/testing on the JVM

### Error handlers

Unique to Rfx is the ability to configure an error handler. An error handler collects all errors that have . The default 

### Event queue

Unique to Rfx is the ability to configure the event queue. Like re-frame, all events in Rfx are [queued and not actioned straight away](https://github.com/day8/re-frame/blob/master/docs/Solve-the-CPU-hog-problem.md).

* `io.factorhouse.rfx.queues.stable` (default) - identical implementation (for now) to re-frame's. 
* `io.factorhouse.rfx.queue.alpha` - subject to change, experimental queue exploring React 19's newer features.

## Learning

We suggest reading the [official re-frame docs](https://github.com/day8/re-frame/tree/master/docs).

## Differences from re-frame

### use-sub vs subscribe

In rfx, there is no need to dereference a subscription inside your component as subscriptions use React hooks (as opposed to RAtoms with re-frame). 

rfx provides a `re-frame.core/subscribe` function with the same API as part of the `io.factorhouse/re-frame-bridge` library. The subscribe function simply wraps the rfx `use-sub` hook in a [delay](https://clojuredocs.org/clojure.core/delay).

```clojure 

;; (require '[re-frame.core :as rf])

(defn test-ui []
  (let [counter (rfx/subscribe [:counter])]
    [:div @counter])) ;; supported, but we prefer use-sub 
```

We suggest using `io.factorhouse.rfx.core/use-sub`.

### Subscriptions can be accessed outside of React!

Use the `io.factorhouse.rfx/snapshot-sub` function. 

There is also a cofx named `:subscription` you can use to inject subscriptions into your Rfx events:

```clojure
(rfx/reg-event-fx :some-event 
  [(rfx/inject-cofx :subscription [:some-sub-id])]
  (fn [{:keys [some-sub-id]} _] 
    ...)
```

If you need raw access to the entire `app-db` outside of React you can:

```clojure 
;; (:require [io.factorhouse.rfx.core :as rfx])

(prn @rfx/app-db)
```

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
