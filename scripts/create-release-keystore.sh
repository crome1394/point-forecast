#!/bin/sh
# Create a Point Forecast upload/release keystore OUTSIDE the git repo.
#
# Env (optional):
#   PF_KEYSTORE_OUT   — default: $HOME/.local/share/point-forecast/point-forecast-release.jks
#   PF_RELEASE_KEY_ALIAS — default: pointforecast
#   PF_KEYSTORE_PASSWORD / PF_KEY_PASSWORD — if unset, generated and printed once
#   PF_DNAME — default: CN=Point Forecast, OU=Mobile, O=crome1394, C=US
#
# After creation, back up the keystore + passwords. Losing them breaks update continuity.
set -eu

OUT=${PF_KEYSTORE_OUT:-"$HOME/.local/share/point-forecast/point-forecast-release.jks"}
ALIAS=${PF_RELEASE_KEY_ALIAS:-pointforecast}
DNAME=${PF_DNAME:-CN=Point Forecast, OU=Mobile, O=crome1394, C=US}
VALIDITY_DAYS=${PF_VALIDITY_DAYS:-10000}

gen_password() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 24 | tr -d '/+=' | head -c 32
    printf '\n'
  else
    # Fallback
    head -c 48 /dev/urandom | base64 | tr -d '/+=\n' | head -c 32
    printf '\n'
  fi
}

if [ -e "$OUT" ]; then
  printf 'Keystore already exists: %s\n' "$OUT" >&2
  printf 'Refusing to overwrite. Move/backup the file first if you intend to replace it.\n' >&2
  exit 1
fi

mkdir -p "$(dirname "$OUT")"

# Modern keytool defaults to PKCS12, which uses a single store password.
if [ -z "${PF_KEYSTORE_PASSWORD:-}" ]; then
  PF_KEYSTORE_PASSWORD=$(gen_password | tr -d '\n')
  GENERATED_STORE=1
else
  GENERATED_STORE=0
fi
PF_KEY_PASSWORD=${PF_KEY_PASSWORD:-$PF_KEYSTORE_PASSWORD}
if [ "$GENERATED_STORE" -eq 1 ]; then
  GENERATED_KEY=1
else
  GENERATED_KEY=0
fi

keytool -genkeypair -v \
  -storetype PKCS12 \
  -keystore "$OUT" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity "$VALIDITY_DAYS" \
  -storepass "$PF_KEYSTORE_PASSWORD" \
  -keypass "$PF_KEY_PASSWORD" \
  -dname "$DNAME"

printf '\n=== Keystore created ===\n'
printf 'Path:  %s\n' "$OUT"
printf 'Alias: %s\n' "$ALIAS"
if [ "$GENERATED_STORE" -eq 1 ] || [ "$GENERATED_KEY" -eq 1 ]; then
  printf '\n*** SAVE THESE PASSWORDS NOW (shown once) ***\n'
  printf 'PF_KEYSTORE_PASSWORD=%s\n' "$PF_KEYSTORE_PASSWORD"
  printf 'PF_KEY_PASSWORD=%s\n' "$PF_KEY_PASSWORD"
  printf '*** Put them in a password manager / encrypted backup ***\n'
fi

printf '\nShell exports for signing (current session):\n'
printf 'export PF_RELEASE_KEYSTORE=%s\n' "$OUT"
printf 'export PF_RELEASE_KEY_ALIAS=%s\n' "$ALIAS"
printf 'export PF_KEYSTORE_PASSWORD='\''…'\''   # from above / your vault\n'
printf 'export PF_KEY_PASSWORD='\''…'\''\n'

printf '\nNext:\n'
printf '  1. Back up the keystore file and passwords offline.\n'
printf '  2. Build unsigned: (cd forecast-point && ./gradlew clean :app:assembleRelease)\n'
printf '  3. Sign: sh scripts/sign-release.sh\n'
printf '  4. Pin cert: write digest to scripts/expected-release-cert.sha256\n'
printf '     (sign-release will fail verify until that file is set; use:\n'
printf '      APKSIGNER_BIN=…/34.0.0/apksigner apksigner verify --print-certs dist/app-release-signed.apk)\n'
