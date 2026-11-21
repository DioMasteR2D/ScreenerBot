# SOLANA HUNTER Android

This is the first Android shell for the ScreenerBot dashboard. It is not a standalone trading engine.
The Rust ScreenerBot service must be deployed separately on a trusted server and exposed over HTTPS.
The app opens that dashboard in an Android WebView and stores the server URL locally.

## Build

From this directory, with JDK 17 and Gradle 8.9+ installed:

    gradle assembleDebug

APK output:

    app/build/outputs/apk/debug/app-debug.apk

GitHub Actions builds the APK on pushes to `solana-hunter/android-app` and on manual dispatch.

## Security

- The app accepts HTTPS endpoints only.
- Do not expose an unauthenticated trading dashboard to the public internet.
- Use a unique strong dashboard password and protect server access.
- Do not paste wallet seed phrases or private keys into the app.
- Live trading should remain disabled until the server, authentication and trade execution have been verified.

## Current scope

The app shell provides branding, a server connection screen, and access to the existing ScreenerBot dashboard. It does not yet embed the Rust engine or provide offline trading.
