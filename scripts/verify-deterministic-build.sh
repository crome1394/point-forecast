#!/bin/sh
# Same-environment smoke test: two clean assembleRelease runs must produce
# identical unsigned APKs. F-Droid's independent rebuild is authoritative.
#
# Run from repo root:
#   sh scripts/verify-deterministic-build.sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$ROOT/forecast-point"

first_dir=$(mktemp -d)
second_dir=$(mktemp -d)
trap 'rm -rf "$first_dir" "$second_dir"' EXIT

gradle_bin=${GRADLE_BIN:-./gradlew}
unsigned_rel=app/build/outputs/apk/release/app-release-unsigned.apk

if ! command -v unzip >/dev/null 2>&1; then
  printf 'unzip is required\n' >&2
  exit 1
fi

hash_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d ' ' -f 1
  else
    shasum -a 256 "$1" | cut -d ' ' -f 1
  fi
}

printf 'Build 1/2…\n'
"$gradle_bin" --no-daemon --no-parallel --no-configuration-cache clean
"$gradle_bin" --no-daemon --no-parallel --no-configuration-cache :app:assembleRelease
if [ ! -f "$unsigned_rel" ]; then
  printf 'Missing unsigned APK after build 1: %s\n' "$unsigned_rel" >&2
  exit 1
fi
cp "$unsigned_rel" "$first_dir/pf.apk"

printf 'Build 2/2…\n'
"$gradle_bin" --no-daemon --no-parallel --no-configuration-cache clean
"$gradle_bin" --no-daemon --no-parallel --no-configuration-cache :app:assembleRelease
cp "$unsigned_rel" "$second_dir/pf.apk"

first_hash=$(hash_file "$first_dir/pf.apk")
second_hash=$(hash_file "$second_dir/pf.apk")

if [ "$first_hash" != "$second_hash" ]; then
  printf 'Release APKs differ across clean builds:\n' >&2
  printf '  %s\n' "$first_hash" >&2
  printf '  %s\n' "$second_hash" >&2
  printf 'See https://gitlab.com/fdroid/wiki/-/wikis/HOWTO:-diff-&-fix-APKs-for-Reproducible-Builds\n' >&2
  exit 1
fi

printf 'Deterministic unsigned APK: %s\n' "$first_hash"
