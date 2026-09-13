#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
gradle="${GRADLE_BIN:-$root/.tools/gradle-8.11.1/bin/gradle}"
[[ -x "$gradle" ]] || { echo 'Run tools/bootstrap-gradle.sh online, or set GRADLE_BIN to Gradle 8.11.1.'; exit 1; }
[[ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]] || { echo 'Set ANDROID_HOME to the prepared Android SDK.'; exit 1; }
exec "$gradle" -p "$root/android" "$@" :app:assembleDebug
