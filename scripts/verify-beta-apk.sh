#!/usr/bin/env bash
#
# M17.1 verification: prove a beta APK can actually be installed, and that the
# ordinary build is unchanged.
#
# An unsigned release APK cannot be installed on anyone's phone, so signing is what
# turns a release build into something that can be handed to a friend. This checks the
# build configuration that does it, against a throwaway key generated here and deleted
# at the end -- the real beta key is the owner's and never reaches this repository:
#
#   1. the default release build is still unsigned, so `./gradlew build` and CI are
#      untouched and no key has leaked into the ordinary build,
#   2. a keystore and its passwords produce a signed, verifiable APK,
#   3. the APK carries the version it was told to carry, so a report names a build,
#   4. it is a beta build: the deployed HTTPS address is in it, and cleartext is
#      forbidden, with no debug domain exception packaged (D033),
#   5. a keystore named without its passwords fails the build, loudly,
#   6. a keystore path that points at nothing fails the build, loudly.
#
# 5 and 6 are the point of the exercise. A half-configured signing setup that quietly
# produced an unsigned APK would be discovered by a friend who cannot install it.
#
# Requires the Android SDK build-tools (apksigner, aapt2) and a JDK (keytool).
# ANDROID_BUILD_TOOLS may name the build-tools directory; otherwise the newest one
# under ANDROID_HOME / ANDROID_SDK_ROOT / sdk.dir in local.properties is used.
#
# Usage:  bash scripts/verify-beta-apk.sh
set -euo pipefail

cd "$(dirname "$0")/.."

BETA_URL=https://chessgame-hit7.onrender.com
OUT=android-app/app/build/outputs/apk/release
# AGP names an unsigned release APK differently, which is itself a signal: the file that
# appears says whether the build was signed.
UNSIGNED=$OUT/android-app-release-unsigned.apk
APK=$OUT/android-app-release.apk
WORK=$(mktemp -d)
KEYSTORE="$WORK/throwaway.p12"
UNPACKED="$WORK/apk"
PASSWORD=throwaway-not-a-real-key
ALIAS=throwaway

log() { printf '\n== %s\n' "$1"; }
fail() { echo "FAILED: $1" >&2; exit 1; }

# The throwaway key is the only secret here, and it dies with the script.
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT

# Gradle is a JVM tool, so on Windows it wants a Windows path for -PchessKeystoreFile.
native_path() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}

case "$(uname -s)" in
  MINGW* | MSYS* | CYGWIN*) GRADLE=./gradlew.bat ;;
  *) GRADLE=./gradlew ;;
esac

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [ -z "$SDK" ] && [ -f local.properties ]; then
  SDK=$(sed -n 's/^sdk\.dir=//p' local.properties | sed 's/\:/:/g' | tr '\' '/')
fi
[ -n "$SDK" ] || fail "no Android SDK: set ANDROID_HOME, or sdk.dir in local.properties"
BUILD_TOOLS=${ANDROID_BUILD_TOOLS:-$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)}
[ -d "$BUILD_TOOLS" ] || fail "no build-tools under $SDK"

# Windows build-tools ship apksigner.bat and aapt2.exe; elsewhere both are extensionless.
if [ -f "$BUILD_TOOLS/apksigner.bat" ]; then
  APKSIGNER="$BUILD_TOOLS/apksigner.bat"
  AAPT2="$BUILD_TOOLS/aapt2.exe"
else
  APKSIGNER="$BUILD_TOOLS/apksigner"
  AAPT2="$BUILD_TOOLS/aapt2"
fi
[ -f "$APKSIGNER" ] || fail "no apksigner in $BUILD_TOOLS"
[ -f "$AAPT2" ] || fail "no aapt2 in $BUILD_TOOLS"

log "1/6 The default release build is still unsigned"
# What CI does. If this ever comes back signed, a key has reached the ordinary build,
# and every build on every machine is publishing it.
rm -f "$APK" "$UNSIGNED"
"$GRADLE" :android-app:assembleRelease --console=plain -q
[ -f "$UNSIGNED" ] || fail "no unsigned release APK at $UNSIGNED"
[ ! -f "$APK" ] || fail "the default build produced $APK, the name AGP uses only when it signs"
if "$APKSIGNER" verify "$UNSIGNED" >/dev/null 2>&1; then
  fail "the default release APK is signed; a key has reached the ordinary build"
fi
echo "unsigned, as the ordinary build should be"

log "2/6 A keystore and its passwords produce a signed APK"
keytool -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 \
  -keyalg RSA -keysize 2048 -validity 1 -alias "$ALIAS" \
  -dname "CN=ChessGame Throwaway, O=Verification, C=US" \
  -storepass "$PASSWORD" -keypass "$PASSWORD" >/dev/null 2>&1
"$GRADLE" :android-app:assembleRelease --console=plain -q \
  "-PchessServerUrl=$BETA_URL" \
  "-PchessKeystoreFile=$(native_path "$KEYSTORE")" \
  "-PchessKeystorePassword=$PASSWORD" \
  "-PchessKeyAlias=$ALIAS" \
  "-PchessKeyPassword=$PASSWORD" \
  "-PchessVersionName=0.0.0-verify" \
  "-PchessVersionCode=999"
"$APKSIGNER" verify "$APK" >/dev/null 2>&1 \
  || fail "the signed build produced an APK that does not verify"
"$APKSIGNER" verify --print-certs "$APK" 2>/dev/null | grep -q 'CN=ChessGame Throwaway' \
  || fail "the APK is signed by something other than the key it was given"
echo "verifies, signed by the key it was given"

log "3/6 The APK says which build it is"
BADGING=$("$AAPT2" dump badging "$APK")
echo "$BADGING" | grep -q "versionCode='999'" || fail "versionCode did not reach the APK"
echo "$BADGING" | grep -q "versionName='0.0.0-verify'" || fail "versionName did not reach the APK"
echo "$BADGING" | head -1

log "4/6 It is a beta build: the deployed address, and no cleartext"
rm -rf "$UNPACKED" && mkdir -p "$UNPACKED"
unzip -q -o "$APK" -d "$UNPACKED"
# The address is a BuildConfig constant, so it is compiled into the dex, not a resource.
grep -rl "$BETA_URL" "$UNPACKED"/classes*.dex >/dev/null 2>&1 \
  || fail "the beta server address is not in the APK"
# Resource file names are shortened in a release APK, so the network security
# configuration is found by what it is rather than by where it was.
NSC=""
for compiled in "$UNPACKED"/res/*.xml; do
  TREE=$("$AAPT2" dump xmltree --file "res/$(basename "$compiled")" "$APK" 2>/dev/null || true)
  case "$TREE" in *network-security-config*) NSC=$TREE; break ;; esac
done
[ -n "$NSC" ] || fail "no network security configuration was packaged"
echo "$NSC" | grep -q 'cleartextTrafficPermitted=false' \
  || fail "the packaged network security configuration does not forbid cleartext"
# D033: the 10.0.2.2/localhost exception belongs to debug builds and nothing else.
case "$NSC" in *domain-config*) fail "the debug cleartext exception was packaged in a beta APK" ;; esac
echo "points at $BETA_URL, and forbids cleartext with no domain exception"

log "5/6 A keystore without its passwords fails the build"
if "$GRADLE" :android-app:assembleRelease --dry-run --console=plain -q \
  "-PchessKeystoreFile=$(native_path "$KEYSTORE")" >/dev/null 2>&1; then
  fail "a keystore with no password configured a build instead of stopping it"
fi
echo "stopped, as it should be"

log "6/6 A keystore path that points at nothing fails the build"
if "$GRADLE" :android-app:assembleRelease --dry-run --console=plain -q \
  "-PchessKeystoreFile=$(native_path "$WORK/absent.p12")" \
  "-PchessKeystorePassword=$PASSWORD" \
  "-PchessKeyAlias=$ALIAS" \
  "-PchessKeyPassword=$PASSWORD" >/dev/null 2>&1; then
  fail "a missing keystore configured a build instead of stopping it"
fi
echo "stopped, as it should be"

log "Leaving the release APK as CI leaves it"
rm -f "$APK" "$UNSIGNED"
"$GRADLE" :android-app:assembleRelease --console=plain -q
[ -f "$UNSIGNED" ] || fail "the unsigned release APK was not rebuilt"
echo "unsigned again"

log "M17.1 beta APK verification complete"
