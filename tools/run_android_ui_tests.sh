#!/usr/bin/env bash
# Collect screenshots before the emulator action shuts the device down.
set -uo pipefail
.tools/gradle-8.11.1/bin/gradle -p android --no-daemon :app:connectedDebugAndroidTest
test_result=$?
mkdir -p android/ui-screenshots
adb pull /sdcard/Android/data/org.tomstout.hermesvoice.ci/files/ui-screenshots android/ui-screenshots/ || true
exit "$test_result"
