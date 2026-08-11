#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

[ -f gradle/wrapper/gradle-wrapper.jar ] || ./scripts/bootstrap-gradle-wrapper.sh
./gradlew --no-daemon --stacktrace testDebugUnitTest lintDebug assembleDebug
