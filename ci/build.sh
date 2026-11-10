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

echo "::group::Android app"
if [ -z "${SIGNING_PASSWORD:-}" ]; then
  echo "::warning::SIGNING_PASSWORD secret is missing, so signing will fail. Add it under Settings > Secrets and variables > Actions."
fi
gradle --no-daemon --stacktrace :app:assembleRelease -PbuildNumber="${BUILD_NUMBER:-1}"
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk "dist/msgs-${BUILD_NUMBER:-1}.apk"
ls -la dist
echo "::endgroup::"
