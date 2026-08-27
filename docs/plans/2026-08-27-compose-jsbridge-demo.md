# BridgeKit Compose JSBridge Demo Implementation Plan

> **For implementation:** Execute each behavior test before its production implementation and keep commits narrowly scoped.

**Goal:** Build an original, public-ready Android JSBridge demonstration with Jetpack Compose, MVVM, an offline H5 page, and reusable bridge abstractions.

**Architecture:** The `bridge` package is pure Kotlin and JVM tested. `container` owns the Android `WebView`, JavaScript injection and navigation/resource interception. `feature.demo` uses a ViewModel to expose UI state to a Compose screen with previews.

**Tech Stack:** Kotlin 2.0.20, Compose, Material 3, AndroidX Lifecycle/ViewModel, kotlinx.serialization, JUnit 4.

---

### Task 1: Scaffold the Android application

Create Gradle settings, application configuration, manifest, Compose theme and a baseline activity. Use SDK 33 because it is the locally installed stable platform; keep `minSdk` 24. Generate a Gradle 8.7 wrapper and prove `assembleDebug` succeeds.

### Task 2: Implement the protocol through JVM tests

Write failing tests for request/response serialization, method registration, callback consumption, parsing failures and JS-to-native dispatch. Implement only the pure Kotlin protocol and registries needed to make each test pass.

### Task 3: Add Android adapters and WebView container

Write/extend JVM tests for the bridge facade. Add the `@JavascriptInterface` adapter, a defensive bootstrap script, trusted-origin checks, `jsbridge://` dispatch, and `localh5://` asset interception. Build the debug APK.

### Task 4: Compose + MVVM demonstration

Implement the `DemoViewModel`, Compose screen/state and `@Preview` functions. Embed the local H5 page in assets; the screen exposes a native-to-JS action and an explanation panel for each showcased capability.

### Task 5: Open-source polish and verification

Create a bilingual README containing architecture, security limits, usage, protocol and test commands. Run all JVM tests and a debug build; inspect the source for author headers and developer comments before committing final work.
