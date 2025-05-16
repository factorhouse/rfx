# RFX

[![test](https://github.com/factorhouse/rfx/actions/workflows/test.yml/badge.svg?branch=main)](https://github.com/factorhouse/rfx/actions/workflows/test.yml)
[![Clojars Project](https://img.shields.io/clojars/v/io.factorhouse/rfx.svg)](https://clojars.org/io.factorhouse/rfx)
[![Clojars Project](https://img.shields.io/clojars/v/io.factorhouse/re-frame-bridge.svg)](https://clojars.org/io.factorhouse/re-frame-bridge)

RFX is a modern, API-compatible drop-in replacement for [re-frame](https://github.com/day8/re-frame), designed for use with React 18+ and **no dependency on Reagent**. Its API is based on hooks.

It integrates seamlessly with vanilla React, popular wrapper libraries like [uix](https://github.com/pitch-io/uix) or even [Reagent](https://github.com/reagent-project/reagent).

**See also:** [HSX](https://github.com/factorhouse/hsx) - a ClojureScript library for writing React components using Hiccup syntax.

## Why?

React 19 introduced significant updates to React's rendering pipeline, which are incompatible with Reagent.

At Factor House, our products require modern React API features without the technical debt of Reagent.

If you want to read more about the engineering challenge of moving a 120k LOC Reagent codebase to React 19 read [this blog post](https://factorhouse.io/blog/articles/beyond-reagent-with-hsx-and-rfx/).

## Getting started

### Option 1: `re-frame.core` API (for migrating codebases)

```clojure 
;; deps.edn
{:deps {io.factorhouse/re-frame-bridge {:mvn/version "0.1.13"}}}
```

The `io.factorhouse/re-frame-bridge` library is a drop-in replacement for [re-frame](https://github.com/day-8/re-frame) allowing RFX use via a `re-frame.core` shim namespace.

This library is intended to be used by existing codebases who are seeking to migrate off Reagent/re-frame.

As this is a compatibility layer, advanced features of RFX (such as contexts and hooks) cannot be used as ergonomically.

Check out the [re-frame-bridge-todo-mvc](examples/re-frame-bridge-todomvc) example for reference.

### Option 2: `io.factorhouse.rfx.core` API (recommended)

```clojure 
;; deps.edn
{:deps {io.factorhouse/rfx {:mvn/version "0.1.13"}}}
```

Consumers of RFX interact with the API through the `io.factorhouse.rfx.core` namespace.

Check out the [rfx-todo-mvc](examples/rfx-todomvc) example for reference.

## Contexts

RFX uses [React Context](https://react.dev/learn/passing-data-deeply-with-context) as the means for components to access the RFX instance.

### Advantages over re-frame

Building on top of React contexts offers several advantages compared to re-frame's global state approach:

- **Component isolation**: Each component can operate within its own instance of RFX
- **Improved testability**: Components can be tested with specific state configurations
- **Better developer experience**: More natural integration with React's component model
- **Tool compatibility**: Easier integration with tools like Storybook

### Basic setup

The `io.factorhouse.rfx.core/RfxContextProvider` provides an RFX instance to its children.

Wrap your root component with an `RfxContextProvider` to get started:

```clojure
;; (:require [io.factorhouse.rfx.core :as rfx])

;; Option 1: Use global application state (like re-frame)
;; Wrapping your root component with no explicit RfxContextProvider uses the global RFX instance:
[my-root-component] ;; Equivalent to [:> rfx/RfxContextProvider #js {} [my-root-component]]

;; Option 2: Initialize your own scoped context
(defonce custom-rfx-ctx (rfx/init {:initial-value {:foo :bar}}))

[:> rfx/RfxContextProvider #js {"value" custom-rfx-ctx}
 [my-root-component]]
```

### Hooks API

| Hook | Description                              |
|------|------------------------------------------|
| `use-sub` | Subscribe to data from the state store   |
| `use-dispatch` | Dispatch events to trigger state changes |
| `use-dispatch-sync` | Dipsatch events immediately rather than queueing |
| `use-rfx-context` | Access to the RFX instance (store, registry, etc) |

An example of API use within a [HSX](https://github.com/factorhouse/hsx) component:

```clojure
;; (require '[io.factorhouse.rfx.core :as rfx])

(rfx/reg-sub :counter (fn [db _] (:counter db)))

(rfx/reg-event-db :counter/increment (fn [db _] (update db :counter inc)))

(defn my-root-component []
  (let [dispatch (rfx/use-dispatch)
        counter  (rfx/use-sub [:counter])]
    [:div {:on-click #(dispatch [:counter/increment])} 
     "The value of counter is " counter]))
```

### Context scoping

The parent `RfxContextProvider` determines:

1. Which store `use-sub` will subscribe to
2. Which event queue `dispatch` will send events to

This context isolation allows components to be developed and tested independently, greatly simplifying integration with tools like [StorybookJS](https://storybook.js.org/).

### Storybook integration example

```clojure
(defmethod storybook/story "Kpow/Sampler/KJQFilter" [_]
  (let [{:keys [dispatch] :as ctx} (rfx/init {})]
    {:component [:> rfx/RfxContextProvider #js {"value" ctx} [kjq/editor "kjq-filter-label"]]
     :stories   {:Filter     {}
                 :ValidInput {:play (fn [_]
                                      (dispatch [:input/context :kjq "foo"])
                                      (dispatch [:input/context :kjq "foo"]))}}}))
```

In this example, each story operates within its own isolated context, allowing components to be tested independently. This approach makes it easier to develop, test, and iterate on individual components when using component-driven development tools.

### Interacting with RFX outside of React

So far you have only seen how to interface with RFX from within React components (via React Contexts and Hooks).

However, you'll often have systems external to React that need to integrate with RFX:

* Routers like [reitit](https://github.com/metosin/reitit) which dispatch when a new page change event is emitted
* WebSocket connections or HTTP responses

External systems need to specify which RFX instance they would like to communicate with via an extra argument when dispatching:

```clojure
(defonce rfx-context (rfx/init {}))

;; Some imaginary ws-instance 
(.on ws-instance "message" #(rfx/dispatch rfx-context [:ws/message %]))
```

This adds little overhead, as it's typical to initialize all your services within an `init` function that has scope to your application's RFX context:

```clojure
(defn init []
  (let [rfx (rfx/init {})]
    (init-ws-conn! rfx)
    (init-reitit-router! rfx)
    (render-my-react rfx)))
```

You can get the current value of a subscription outside of a React context by calling `io.factorhouse.rfx.core/snapshot-sub`:

```clojure 
(defn codemirror-autocomplete-suggestions 
  [rfx]
  (let [database-completions (rfx/snapshot-sub rfx [:ksql/database-completions])]
    ;; Logic to wire up codemirror6 completions based on re-frame data goes here
    ))
```

This might be one of RFX's major selling points! Accessing subscriptions outside of React with re-frame has always been cumbersome and somewhat hacky.

You can access the current value of the application db by calling `io.factorhouse.rfx.core/snapshot`.

**Note**: Both `snapshot` and `snapshot-sub` are not 'reactive' - they will not cause a re-render of a component when values change. These functions are intended to be used outside a React context.

### Configuring the RFX Instance

Calling `io.factorhouse.rfx.core/init` returns a new RFX instance. So far we have only seen how to use this instance, but not how to configure it.

`rfx/init` accepts a map with the following keys:

| Key | Required? | Description |
|-----|:--------:|-------------|
| `:queue` | ❌ | The event queue used to process messages. Default queue is the same as re-frame's (uses goog.async.nextTick to process events) |
| `:error-handler` | ❌ | Error handler (default ErrorHandler is the same as re-frame's - something that logs and continues) |
| `:store` | ❌ | The store used to house your application's state. Default store is backed by a Clojure atom. |
| `:initial-value` | ❌ | The initial value of the store. Default is `{}`. |
| `:registry` | ❌ | The event+subscription registry the RFX instance will use. Defaults to the global registry. |

## Differences from re-frame

### All subscriptions are React Hooks

Even the `re-frame.core/subscribe` shim function returns a subscription hook wrapped in a [Clojure delay](https://clojuredocs.org/clojure.core/delay).

This means you can use RFX from any React wrapper (like HSX or Uix) or even plain JavaScript.

**Note:** All the caveats of [React hooks](https://react.dev/reference/rules/rules-of-hooks) also apply to RFX subscriptions!

Reagent users will need to wrap components in the `:f>` function component shorthand:

```clojure
(defn rfx-interop []
  (let [val @(re-frame.core/subscribe [:some-value])]
    [:div "The result is " val]))

(defn my-reagent-comp [] 
  [:f> rfx-interop])
```

### `^:flush-dom` annotations

`^:flush-dom` metadata is not supported like in re-frame.

## Learning the re-frame architecture

We highly recommend reading the excellent [official re-frame docs](https://github.com/day8/re-frame/tree/master/docs) to understand the architecture that RFX builds upon.

## License

Distributed under the Apache 2.0 License.

Copyright (c) [Factor House](https://factorhouse.io)
