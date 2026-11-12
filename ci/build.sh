#!/usr/bin/env bash
# Builds the Go engine (gomobile AAR) and the signed release APK.
# Run from the repo root. Needs Go, Java 17, Gradle, the Android SDK and NDK.
set -euo pipefail

# Pinned version of the Google Messages pairing library (mautrix-gmessages).
LIBGM_REF="0956207e461ddc32d16cfd40db414962554409aa"

echo "::group::Go engine"
cd bridge
go get "go.mau.fi/mautrix-gmessages@${LIBGM_REF}"
go mod tidy
go get golang.org/x/mobile/bind@latest
go vet ./... || true
go build ./...

go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
export PATH="$PATH:$(go env GOPATH)/bin"
export ANDROID_NDK_HOME="${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_HOME:-}}"
echo "Using NDK at ${ANDROID_NDK_HOME}"
gomobile init
mkdir -p ../app/libs
gomobile bind -target=android/arm64 -androidapi 26 -o ../app/libs/gmbridge.aar .
ls -la ../app/libs
cd ..
echo "::endgroup::"

echo "::group::Signing key"
# The key never lives in the repo. If the MSGS_KEYSTORE_B64 and
# MSGS_KEYSTORE_PASSWORD secrets exist, use them. Otherwise make a throwaway key
# for this one build: it is never saved anywhere, so a later build can't update
# this one (uninstall first).
KS_DIR="${RUNNER_TEMP:-$(mktemp -d)}"
export MSGS_KEYSTORE="$KS_DIR/msgs-signing.p12"
if [ -n "${MSGS_KEYSTORE_B64:-}" ] && [ -n "${MSGS_KEYSTORE_PASSWORD:-}" ]; then
  echo "$MSGS_KEYSTORE_B64" | base64 -d > "$MSGS_KEYSTORE"
  echo "Using the signing key from repository secrets"
else
  MSGS_KEYSTORE_PASSWORD="$(openssl rand -hex 24)"
  echo "::add-mask::$MSGS_KEYSTORE_PASSWORD"
  keytool -genkeypair -keystore "$MSGS_KEYSTORE" -storetype pkcs12 -storepass "$MSGS_KEYSTORE_PASSWORD" \
    -keypass "$MSGS_KEYSTORE_PASSWORD" -alias msgs -keyalg EC -groupname secp256r1 -validity 3650 \
    -dname "CN=Msgs R4 test build" >/dev/null
  echo "No signing secrets set: generated a one-time key for this build"
fi
export MSGS_KEYSTORE_PASSWORD
{ keytool -list -v -keystore "$MSGS_KEYSTORE" -storepass "$MSGS_KEYSTORE_PASSWORD" | grep "SHA256:" | head -n1 | sed 's/^/Signing certificate /'; } || true
echo "::endgroup::"

echo "::group::Android app"
gradle --no-daemon --stacktrace :app:assembleRelease -PbuildNumber="${BUILD_NUMBER:-1}"
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk "dist/msgs-${BUILD_NUMBER:-1}.apk"
rm -f "$MSGS_KEYSTORE"
(cd dist && sha256sum "msgs-${BUILD_NUMBER:-1}.apk")
ls -la dist
echo "::endgroup::"
