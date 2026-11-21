# SOLANA HUNTER — Secure server connection

The Android app is a mobile client for the existing ScreenerBot web dashboard. The Rust trading engine and its wallet remain on the server. The app does not contain private keys and does not execute swaps locally.

## Required server setup

1. Run the ScreenerBot headless/Linux service on a trusted machine.
2. In the headless webserver configuration, bind to the private interface needed by your tunnel or reverse proxy. The documented default is `127.0.0.1:8080`; remote access may require a bind address such as `0.0.0.0`.
3. Enable the built-in dashboard password authentication and set a unique, strong password. Configure TOTP where available.
4. Put the dashboard behind HTTPS using a reverse proxy with a valid certificate, or access it through a private VPN that provides a trusted HTTPS hostname/certificate.
5. Do not expose port 8080 directly to the public internet. Restrict firewall access to the VPN/reverse-proxy path.
6. In SOLANA HUNTER, enter the HTTPS dashboard origin, for example `https://bot.example.net`.

## Important security notes

- The app rejects plain HTTP URLs and blocks mixed content.
- The WebView allows JavaScript because the existing dashboard requires it, but disables file and content access.
- Dashboard cookies are enabled for login sessions; third-party cookies are disabled.
- The app stores only the dashboard URL. It does not request or store wallet seed phrases or private keys.
- Never send seed phrases, private keys, or API secrets in chat or in a dashboard URL.
- Keep live trading disabled until server authentication, RPC connectivity, token-risk filters, and actual order execution have been independently verified.

## Current app scope

The app wraps the existing ScreenerBot dashboard in an Android WebView. This exposes the server's existing pages and API-backed functions when the server is configured and reachable. It is not an offline/mobile-native rewrite of the Rust engine. Dashboard layout on small screens still needs device-by-device validation.
