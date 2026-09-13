> **Current version 0.3:** See [TEST_REPORT_v0.3.md](TEST_REPORT_v0.3.md) for current software checks and [the workshop guide](docs/HARDWARE-GUIDE.md) for wiring, programming and physical acceptance. The text below is retained as historical provenance.

> **Historical record:** The original text below describes an earlier authoring session. Both targets subsequently compiled in run `34752997335`; see [README.md](README.md) and [VERIFIED_BUILD.md](VERIFIED_BUILD.md) for current status. Physical hardware acceptance remains outstanding.

# Test report — v0.2 source-only update

## Completed in this session

- The original 56 host tests passed before changes.
- After the endpoint and build-script changes, **62 host tests passed**.
- The new six tests cover endpoint/board consistency and rejection of malformed
  Intel HEX, non-ARM ELF, and renamed source ZIPs. Tiny synthetic format fixtures
  are created in temporary test directories and removed; they are not firmware.
- Python syntax compilation for the new build helper passed.
- Bash syntax checking of `build-all.sh` passed.
- The build-prerequisite check exited with status 2 and correctly listed missing
  Gradle, Android SDK/build-tools, and PlatformIO. No target build was run.

## Not completed or verified

- No Android target compilation, APK signing, APK installation, or Pixel testing.
- No Seeed/Zephyr target compilation, firmware flashing, or board bench testing.
- No Android/firmware build-wrapper end-to-end execution.
- The PowerShell wrapper was not executed; PowerShell is not available here.
- No live connection to `100.99.200.55` or installation on that machine.
- No actual charger-current, battery-life, capture-latency, microphone, BLE, or
  locked-screen measurements.

Detailed output is in `build-logs/session/`. No APK, HEX, BIN, or ELF is included.
A successful host-test run must not be interpreted as a successful target build.
