# Msgs

A custom texting app for Android that keeps full RCS. It pairs with Google
Messages the same way Messages for web does: Google Messages stays installed and
does the RCS and SMS work in the background, and Msgs is the app you actually use.

## How it fits together

- `bridge/` is the engine, written in Go. It wraps
  [mautrix-gmessages](https://github.com/mautrix/gmessages)' `libgm` package and
  is compiled for Android with gomobile.
- `app/` is the Android app (Kotlin, Jetpack Compose).
- `.github/workflows/build.yml` builds both on every push to `main` and publishes
  the APK as a GitHub pre-release. The signing key is never in the repo: CI uses
  the `MSGS_KEYSTORE_B64` and `MSGS_KEYSTORE_PASSWORD` secrets if they exist,
  otherwise a one-time key for that build only.
- R4 test builds pair by QR code only, keep the pairing keys encrypted with an
  Android Keystore key, and have deleting and the in-app updater turned off.
- The `ci-logs` branch holds the log of the latest build.

## Licence

The engine links `libgm`, which is AGPL-3.0, so this app's source must stay
available to anyone you give the app to. Keeping this repo public covers that.
