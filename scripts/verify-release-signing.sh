#!/bin/sh
# Verify a signed APK uses the expected Point Forecast release certificate.
#
# Expected SHA-256 digest is read from scripts/expected-release-cert.sha256
# (lowercase hex, no colons). Override with PF_EXPECTED_CERT_SHA256.
#
# Usage:
#   sh scripts/verify-release-signing.sh path-to-signed-apk
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
apk_path=${1:?Usage: verify-release-signing.sh path-to-signed-apk}

if [ ! -f "$apk_path" ]; then
  printf 'APK not found: %s\n' "$apk_path" >&2
  exit 1
fi

if [ -n "${PF_EXPECTED_CERT_SHA256:-}" ]; then
  expected_sha256=$(printf '%s' "$PF_EXPECTED_CERT_SHA256" | tr '[:upper:]' '[:lower:]' | tr -d ':\n ')
else
  expected_file="$ROOT/scripts/expected-release-cert.sha256"
  if [ ! -f "$expected_file" ]; then
    printf 'Missing %s\n' "$expected_file" >&2
    printf 'Create the release keystore (scripts/create-release-keystore.sh), sign an APK,\n' >&2
    printf 'then write the cert digest into that file (see docs/REPRODUCIBLE_BUILDS.md).\n' >&2
    exit 1
  fi
  expected_sha256=$(tr '[:upper:]' '[:lower:]' < "$expected_file" | tr -d ':\n ')
fi

if [ -z "$expected_sha256" ] || [ "$expected_sha256" = "REPLACE_AFTER_KEYSTORE_CREATED" ]; then
  printf 'expected-release-cert.sha256 is not set yet.\n' >&2
  printf 'Run create-release-keystore.sh, sign a test APK, then pin the digest.\n' >&2
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
  exit 1
}

apksigner_bin=$(resolve_apksigner)

cert_output=$("$apksigner_bin" verify --print-certs "$apk_path")
digests=$(printf '%s\n' "$cert_output" | sed -n 's/^Signer #[0-9][0-9]* certificate SHA-256 digest: //p' | tr '[:upper:]' '[:lower:]' | tr -d ':')
signer_count=$(printf '%s\n' "$digests" | sed '/^$/d' | wc -l | tr -d ' ')

if [ "$signer_count" -ne 1 ]; then
  printf 'Expected exactly one APK signer, found %s.\n' "$signer_count" >&2
  exit 1
fi

actual_sha256=$(printf '%s\n' "$digests" | sed -n '1p')
if [ "$actual_sha256" != "$expected_sha256" ]; then
  printf 'Unexpected APK signing certificate.\n' >&2
  printf 'Expected: %s\n' "$expected_sha256" >&2
  printf 'Actual:   %s\n' "$actual_sha256" >&2
  exit 1
fi

printf 'Release signing certificate verified: %s\n' "$actual_sha256"
