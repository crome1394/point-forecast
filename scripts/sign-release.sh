#!/bin/sh
# Sign an unsigned release APK with the Point Forecast release key.
# Uses Android Build Tools 34 apksigner (F-Droid-compatible for upstream-signed RB).
#
# Required env:
#   PF_RELEASE_KEYSTORE, PF_RELEASE_KEY_ALIAS,
#   PF_KEYSTORE_PASSWORD, PF_KEY_PASSWORD
# Optional:
#   APKSIGNER_BIN  — override path to apksigner
#
# Usage:
#   sh scripts/sign-release.sh [unsigned.apk] [out-signed.apk]
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$ROOT"

unsigned_apk=${1:-forecast-point/app/build/outputs/apk/release/app-release-unsigned.apk}
signed_apk=${2:-dist/app-release-signed.apk}

: "${PF_RELEASE_KEYSTORE:?Set PF_RELEASE_KEYSTORE to the private release keystore path.}"
: "${PF_RELEASE_KEY_ALIAS:?Set PF_RELEASE_KEY_ALIAS to the release key alias.}"
: "${PF_KEYSTORE_PASSWORD:?Set PF_KEYSTORE_PASSWORD in the environment.}"
: "${PF_KEY_PASSWORD:?Set PF_KEY_PASSWORD in the environment.}"

if [ ! -f "$unsigned_apk" ]; then
  printf 'Unsigned release APK not found: %s\n' "$unsigned_apk" >&2
  exit 1
fi

if [ ! -f "$PF_RELEASE_KEYSTORE" ]; then
  printf 'Release keystore not found: %s\n' "$PF_RELEASE_KEYSTORE" >&2
  exit 1
fi

if [ "$unsigned_apk" = "$signed_apk" ]; then
  printf 'Input and output APK paths must be different.\n' >&2
  exit 1
fi

resolve_apksigner() {
  if [ -n "${APKSIGNER_BIN:-}" ]; then
    printf '%s\n' "$APKSIGNER_BIN"
    return
  fi

  for root in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}"; do
    if [ -n "$root" ] && [ -x "$root/build-tools/34.0.0/apksigner" ]; then
      printf '%s\n' "$root/build-tools/34.0.0/apksigner"
      return
    fi
  done

  printf 'Android Build Tools 34 apksigner not found.\n' >&2
  printf 'Install build-tools;34.0.0 or set APKSIGNER_BIN explicitly.\n' >&2
  printf 'Do not use Build Tools 35+ apksigner for F-Droid upstream-signed APKs.\n' >&2
  exit 1
}

apksigner_bin=$(resolve_apksigner)
if [ ! -x "$apksigner_bin" ]; then
  printf 'apksigner is not executable: %s\n' "$apksigner_bin" >&2
  exit 1
fi

mkdir -p "$(dirname "$signed_apk")"

cleanup=1
trap 'if [ "$cleanup" -eq 1 ]; then rm -f "$signed_apk" "$signed_apk.idsig"; fi' EXIT HUP INT TERM

cp "$unsigned_apk" "$signed_apk"

"$apksigner_bin" sign \
  --ks "$PF_RELEASE_KEYSTORE" \
  --ks-key-alias "$PF_RELEASE_KEY_ALIAS" \
  --ks-pass env:PF_KEYSTORE_PASSWORD \
  --key-pass env:PF_KEY_PASSWORD \
  "$signed_apk"

APKSIGNER_BIN="$apksigner_bin" sh "$ROOT/scripts/verify-release-signing.sh" "$signed_apk"

cleanup=0
trap - EXIT HUP INT TERM
printf 'Signed release APK: %s\n' "$signed_apk"
