# ScreenerBot for Android

This is a native Android shell around the existing Rust ScreenerBot engine and its
embedded dashboard. The engine is compiled as an ARM64 JNI shared library and
runs inside the app process; the dashboard is served only on loopback and opened
inside an Android WebView.

## Build

The GitHub Actions workflow at `.github/workflows/android.yml` builds an
installable, debug-signed APK for `arm64-v8a` on pushes to this branch and on
manual dispatch:

1. Cross-compile the Rust library for `aarch64-linux-android`.
2. Package it as `lib/arm64-v8a/libscreenerbot.so`.
3. Build the Android APK with Gradle.
4. Verify the APK package metadata and upload it as
   `ScreenerBot-Android-ARM64-debug`.

Download the APK from the successful run's **Artifacts** section. A debug APK
is suitable for sideloaded testing; a production release still needs a
separately managed signing key and release process.

## Runtime and security

- The Android foreground service starts the existing Rust boot sequence and
  keeps the engine alive while the dashboard Activity is closed.
- Android always starts the backend in the existing GUI security mode: a
  dynamic loopback port and per-process session token. The app injects that
  token only into same-origin dashboard API requests; it is not put in the URL.
- Cleartext HTTP is permitted only for `127.0.0.1` and `localhost`.
- Wallet-encryption identity is encrypted at rest with a non-exportable
  Android Keystore AES-GCM key. The Rust engine receives the unwrapped
  identifier only in its process environment.
- App data is private to the Android application and Android backup is disabled.

The dashboard remains the existing ScreenerBot dashboard, so token discovery,
filtering, charts, strategies, positions, wallet management and trading controls
are supplied by the same engine/routes as desktop and headless builds.

## Language\n\n- The Android startup screen and foreground-service notifications are in Greek.\n- The dashboard sends `el-GR` as its preferred language and includes a Greek locale catalog for navigation, common controls, the home screen, Auto Trader, positions, token details, wallets and setup. Untranslated dashboard messages fall back to English until their Greek catalog entries are completed.\n- The Greek locale can also be selected from the dashboard language setting.\n\n## Current scope

- First target: ARM64 Android devices (`arm64-v8a`).
- Build type: debug-signed APK for installation/testing.
- This does not change the upstream trading strategy or enable live trading by
  itself. Wallet/RPC setup and the engine's existing safety/readiness gates
  remain in effect.
- The source repository is licensed under BUSL-1.1. Review the license before
  redistribution or commercial use.
