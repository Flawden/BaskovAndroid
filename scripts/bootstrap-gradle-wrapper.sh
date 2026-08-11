#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

if [ -f gradle/wrapper/gradle-wrapper.jar ]; then
  echo "Gradle wrapper already exists."
  exit 0
fi

if command -v gradle >/dev/null 2>&1 && gradle --version | grep -q 'Gradle 8\.13'; then
  gradle wrapper --gradle-version 8.13 --distribution-type bin
  echo "Gradle wrapper generated from installed Gradle 8.13."
  exit 0
fi

TMP=${TMPDIR:-/tmp}/baskov-gradle-8.13
ZIP=$TMP/gradle-8.13-bin.zip
HOME_DIR=$TMP/gradle-8.13
mkdir -p "$TMP"

if [ ! -x "$HOME_DIR/bin/gradle" ]; then
  command -v curl >/dev/null 2>&1 || { echo "curl is required" >&2; exit 1; }
  command -v unzip >/dev/null 2>&1 || { echo "unzip is required" >&2; exit 1; }
  echo "Downloading official Gradle 8.13 distribution..."
  curl -fL --retry 3 -o "$ZIP" https://services.gradle.org/distributions/gradle-8.13-bin.zip
  unzip -q -o "$ZIP" -d "$TMP"
fi

"$HOME_DIR/bin/gradle" wrapper --gradle-version 8.13 --distribution-type bin
echo "Gradle wrapper generated. Next: ./scripts/android-gate.sh"
