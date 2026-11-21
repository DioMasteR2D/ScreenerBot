# SOLANA HUNTER Android

SOLANA HUNTER is the Android client shell for the existing ScreenerBot dashboard. The Rust trading engine stays on the trusted server; the Android app connects to its existing dashboard over HTTPS.

## Build

Requires JDK 17 and Gradle 8.9+:

    cd android
    gradle assembleDebug

APK output:

    app/build/outputs/apk/debug/app-debug.apk

GitHub Actions builds the debug APK on pushes to `solana-hunter/android-app` that touch `android/**`, and on manual dispatch. The downloadable Actions artifact is named `solana-hunter-debug-apk`.

## Connect

1. Deploy ScreenerBot in headless mode on a trusted Linux server.
2. Enable dashboard password authentication.
3. Put the dashboard behind valid HTTPS, preferably reachable only through a private VPN.
4. Open SOLANA HUNTER and enter the HTTPS dashboard URL.
5. Sign in using the ScreenerBot dashboard's own authentication.

See [SECURE_SERVER_SETUP.md](SECURE_SERVER_SETUP.md) before exposing the server to any network.

## Security

- HTTPS URLs only; mixed content is blocked.
- WebView file/content access is disabled.
- Dashboard cookies are enabled for the first-party login session; third-party cookies are disabled.
- The app stores the dashboard URL only. It does not collect wallet seed phrases or private keys.
- Do not expose the headless dashboard's port directly to the public internet.
- Keep live trading disabled until authentication, RPC, risk checks and order execution have been verified.

## Scope and limitations

The app wraps the existing ScreenerBot dashboard, so the dashboard's existing pages and API-backed features are available when the server is running and reachable. This is not an offline Android port of the Rust trading engine. Responsive layout, authentication flows and all trading actions still require validation on a real Android device before live use.
