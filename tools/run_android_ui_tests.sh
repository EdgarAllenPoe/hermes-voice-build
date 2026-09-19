#!/usr/bin/env bash
# Collect screenshots before the emulator action shuts the device down.
set -uo pipefail
.tools/gradle-8.11.1/bin/gradle -p android --no-daemon :app:connectedDebugAndroidTest
test_result=$?
mkdir -p android/ui-screenshots
adb pull /sdcard/Download/hermes-ui-screenshots android/ui-screenshots/ || true
if [ "$test_result" -eq 0 ] && [ "$(find android/ui-screenshots -name '*.png' | wc -l)" -lt 7 ]; then
  echo "Android tests passed but expected UI screenshots were not retained."
  exit 1
fi
exit "$test_result"
