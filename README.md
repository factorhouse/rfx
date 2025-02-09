# Rfx

Rfx is a modern, API-compatible drop-in replacement for [re-frame](https://github.com/day8/re-frame), designed for use with React 18+ and **no dependency on Reagent**. Its API is based on hooks.

It integrates seamlessly with vanilla React, popular wrapper libraries like [uix](https://github.com/pitch-io/uix) or even [Reagent](https://github.com/reagent-project/reagent).

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

This library is part of our ongoing efforts at Factor House to migrate large ClojureScript codebases to modern React without sacrificing the core principles and patterns that make re-frame powerful.

### Features

* **API Compatibility**: Drop-in replacement for re-frame—no need to rewrite your event handlers or subscriptions.
* **Modern React Support**: Fully compatible with React 18+, including features like Concurrent Rendering.
* **Performance**: rfx takes full advantage of React's batched updates ... 
* **Flexible Storage**: Pluggable storage backend for state management, with a ClojureScript atom as the default backend.
* **Framework Agnostic**: Works with vanilla React, uix, and even Reagent (though migrating from Reagent is recommended).
* **Future-Proof**: Stay aligned with React's ongoing development and ecosystem advancements.

## Getting started

### Option 1: `re-frame.core` API

```clojure 
;; deps.edn
{:deps {io.factorhouse/re-frame-bridge {:mvn/version "1.0.0"}}}
```

The `io.factorhouse/re-frame-bridge` library is a drop-in replacement for [re-frame](https://github.com/day-8/re-frame) allowing you to interface with Rfx through a familiar `re-frame.core` namespace. 

This library is primarily intended to be used by existing codebases who are seeking to migrate off Reagent/Re-frame.

A compatability layer has been written for the `re-frame.core` namespace and all public functions have been implemented. 

As this is a compatability layer, advanced features of Rfx (such as React Contexts, hooks etc) cannot be used as ergonomically from code using the bridging library.

Check out the [re-frame-bridge-todo-mvc]() example for reference.

### Option 2: `io.factorhouse.rfx.core` API

```clojure 
;; deps.edn
{:deps {io.factorhouse/rfx {:mvn/version "1.0.0"} ;; Core library
        io.factorhouse/rfx-dev {:mvn/version "1.0.0"} ;; Useful (and optional) DX tools
        }}
```

The `io.factorhouse/rfx` library presents a Re-frame like architecture built on modern React foundations. Consumers of this library interface with the API primarily through the `io.factorhouse.rfx.core` namespace.

Check out the [rfx-todo-mvc]() example for reference.

#### Rfx: Contexts

Rfx uses [React Context](https://react.dev/learn/passing-data-deeply-with-context) for child components wanting access global application state (subscriptions, dispatching events, accessing the state store etc).

By reading this section you will see how building on top of React contexts has several advantages to re-frame's singleton state approach.

The `io.factorhouse.rfx.core/RfxContextProvider` component represents the entrypoint to Rfx.

Simply wrap your root component around a `RfxContextProvider` and you are ready to go:

```clojure 
;; (:require [io.factorhouse.rfx.core :as rfx])

;; Option 1: use global application state (like re-frame)
;; Wrapping your root component with no RfxContextProvider means your UI will use the default global state/configuration:
[my-root-component] ;; Equivilant to [:> rfx/RfxContextProvider #js {} [my-root-component]]

;; Option 2: initialize your own scoped context
(defonce custom-rfx-ctx (rfx/init {:initial-value {:foo :bar}}))

[:> rfx/RfxContextProvider #js {"value" custom-rfx-context}
 [my-root-component]]
```

Rfx exposes two hooks to interface with global application state:

* `io.factorhouse.rfx/use-sub` - a hook to subscribe to some data
* `io.factorhouse.rfx/use-dispatch` - a hook to dispatch an event

Both of these can be used within `my-root-component` like so:

```clojure
(rfx/reg-sub :counter (fn [db _] (:counter db)))

(rfx/reg-event-db :counter/increment (fn [db _] (update db :counter inc)))

(defn my-root-component
  (let [dispatch (rfx/use-dispatch)
        counter  (rfx/use-sub [:counter])]
    [:div {:on-click #(dispatch [:counter/increment])} "The value of counter is " counter]))
```

Depending on the value of the `RfxContextProvider` from a parent component will dictate:

1) Which store `use-sub` will subscribe to
2) Which event queue `dispatch` will send events to

This allows for your components to be isolated and thus allows for easier testing/integration with tools such as [StorybookJS](https://storybook.js.org/):

```clojure
(defmethod storybook/story "Kpow/Sampler/KJQFilter" [_]
  (let [{:keys [dispatch] :as ctx} (rfx/init {})]
    {:component [:> rfx/ReactContextProvider #js {"value" ctx} [kjq/editor "kjq-filter-label"]]
     :stories   {:Filter     {}
                 :ValidInput {:play (fn [x]
                                      (dispatch [:input/context :kjq "foo"])
                                      (dispatch [:input/context :kjq "foo"]))}}}))
```

In this example, each story operates within its own isolated context, allowing components to be tested independently without relying on the global application state. This makes it easier to develop, test, and iterate on individual components, especially when using tools like Storybook for component-driven development.


#### Rfx: accessing context outside of React 

So far you have only seen how we interface with Rfx from within React components (via Contexts and Hooks). 

However, oftentimes you will have systems external to React that want to integrate with Rfx: 

* Routers like [reitit](https://github.com/metosin/reitit) which dispatch when a new page change event is emitted
* WebSocket connections or HTTP responses

External systems will need to specify which Rfx instance they would like to communicate with via an extra argument:

```clojure
(defonce rfx-context (rfx/init {}))

;; Some imaginary ws-instance 
(.on ws-instance "message" #(rfx/dispatch rfx-context [:ws/message %]))
```

Generally this is little overhead, as you it's typical to initialize all your services from inside an `init` function that has scope to your applications Rfx context:

```clojure
(defn init []
  (let [rfx (rfx/init {})]
    (init-ws-conn! rfx)
    (init-reitit-router! rfx)
    (render-my-react rfx)))
```

You can get the current snapshot of a subscription outside of a React context by calling `io.factorhouse.rfx.core/snapshot-sub`:

```clojure 
(defn codemirror-autocomplete-suggestions 
  [rfx]
  (let [database-completions (rfx/snapshot-sub rfx [:ksql/database-completions])
    ;; Logic to wire up codemirror6 completions based on re-frame data goes here
    ))
```

If anything this might be Rfx's major selling point! Accessing subscriptions outside of React with re-frame has always been cumbersome and somewhat hacky.

You can access the current value of the application db by calling `io.factorhouse.rfx.core/snapshot-state`.

**Note**: both `snapshot-state` and `snapshot-sub` are not 'reactive' - as in, they will not cause a re-render of a component when its value changes. These two functions are intended to be used outside of a React context.

#### Configuring the Rfx instance

Calling `io.factorhouse.rfx.core/init` returns a new Rfx instance. So far we have only seen how we use this instance, but not how we configure it:

`rfx/init` accepts the following keys: 

* `:queue` - (optional) the event queue used to process messages. Default Queue is the same as re-frame's (uses goog.async.nextTick to handle events)
* `:error-handler` - (optional) error handler (default ErrorHandler is the same as re-frame's - something that logs and continues)
* `:store` - (optional) the store used to house your applications state. Default store is backed by a Clojure atom.
* `:initial-value` - (optional) the initial value of the store. Default is `{}`.

#### Custom error handlers

Error handlers allow you to deal with errors 

#### Custom stores and queues

The default queue and store are sufficient for most use cases, however, it is possible to:

* Write a custom store backend targeting popular JavaScript libraries like [zustand](https://github.com/pmndrs/zustand) or even [DataScript](https://github.com/tonsky/datascript). 
* Write a queue based on [p-queue](https://github.com/sindresorhus/p-queue) so that you can compose Rfx events with React features like [useTransition](https://react.dev/reference/react/useTransition).

While we have no guide on writing your custom store or queue (yet), please keep in mind that this extensibility is fundamental to Rfx.

This is an area of active research here at Factor House as we explore the possibility of bringing more modern React capabilities into the ClojureScript React ecosystem.

#### Developer tools

Rfx comes with rich developer tools via the `io.factorhouse/rfx-dev` dependency.

Simply wrap your Rfx instance at development time with the `io.factorhouse.rfx.dev/wrap-dev` function:

```clojure 
;; (:require [io.factorhouse.rfx.core :as rfx]
;;           [io.factorhouse.rfx.dev :as rfx-dev])

(defn dev-init! []
  (let [rfx (-> {} rfx/init wrap-dev)]
    (render-my-react rfx)))
```

`wrap-dev` adds all instrumentation, introspection and tracing utilities needed for developers when building Rfx applications.

## Differences from re-frame

### `^:flush-dom` annotations

`^:flush-dom` metadata does not use `reagent...` but instead uses [requestAnimationFrame](https://developer.mozilla.org/en-US/docs/Web/API/Window/requestAnimationFrame). This feature is something we at Factor House have never used, so have not prioritised supporting it in any serious way with React 18. PRs welcome.

### Rfx uses React 18+'s automatic batched updates + concurrent rendering out of the box

The default Rfx store is implemented by the [useSyncExternalStore](https://react.dev/reference/react/useSyncExternalStore) hook. This allows Rfx to take advantage of [React's automatic batching](https://medium.com/@Brahmbhatnilay/understanding-automatic-batching-in-react-18-enhancing-performance-with-optimized-state-updates-9658d04e5785) found in React 18+. This is in contrast to Reagent's own batching logic for RAtoms that does not play well with modern React.

**Note:** if your application depends on the specific timing of updates that is coupled to Reagent's implementation details then it's possible you might experience race conditions when migrating to Rfx.

### All subscriptions are React hooks! 

Even the compatible `re-frame.core/subscribe` function returns a subscription hook wrapped in a [Clojure delay](https://clojuredocs.org/clojure.core/delay).

This means you will be able to use Rfx from any modern React wrapper (like HSX or Uix) or even vanilla JavaScript.

**Note:** this means all the caveats of [React hooks](https://react.dev/reference/rules/rules-of-hooks) also apply to Rfx subscriptions! This also includes the re-frame-bridge compatibility layer.

Reagent users will have to wrap components in the `:f>` shorthand to indicate you are inside a functional component:

```clojure
(defn rfx-interop []
  (let [val @(rf/subscribe [:some-value])]
    [:div "The result is " val]))

(defn my-reagent-comp [] 
  [:f> rfx-interop])
```

## Learning the re-frame architecture

We highly suggest reading the most excellent [official re-frame docs](https://github.com/day8/re-frame/tree/master/docs).

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
