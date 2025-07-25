# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).


## [0.1.18] - 2025-07-02

### Added
- rfx+re-frame-bridge: Add `:fx` reg-fx to. Thanks to @valerauko!

### Fixed
- re-frame-bridge: Support `:->` sugar in `reg-sub`. Thanks to @valerauko!


## [0.1.17] - 2025-06-02

### Fixed
- rfx+re-frame-bridge: fix `clear-subscription-cache!`

## [0.1.16] - 2025-05-30

### Changed
- rfx: Add `clear-subscription-cache!` method to `Store` protocol

### Breaking
- rfx: `reg-fx` now two-arity

### Fixes
- rfx+re-frame-bridge: fix `clear-subscription-cache!` impl

## [0.1.15] - 2025-05-17
### Fixes
- rfx: [clear-subscription-cache! arity for specific RFX instance](https://github.com/factorhouse/rfx/commit/3880578adaf6df31cf386eca191336ec963dea50)
- rfx: [dispatch-sync fn uses scoped registry](https://github.com/factorhouse/rfx/commit/cacc99ade6ee87f9cab0a7562f9e5a8fed1121d2)

## [0.1.14] - 2025-05-15
### Changed
- Original release of RFX!
