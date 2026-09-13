> **Current version 0.3:** See [TEST_REPORT_v0.3.md](TEST_REPORT_v0.3.md) for current software checks and [the workshop guide](docs/HARDWARE-GUIDE.md) for wiring, programming and physical acceptance. The text below is retained as historical provenance.

> **Historical record:** The original text below describes an earlier authoring session. Both targets subsequently compiled in run `34752997335`; see [README.md](README.md) and [VERIFIED_BUILD.md](VERIFIED_BUILD.md) for current status. Physical hardware acceptance remains outstanding.

# Hermes Voice Button — configured source and build scripts

**Status: no APK or ready-to-flash firmware was produced in this session.**

This is a source-only update, not a binary release. It must not be described as an
installable Android app or flashable firmware package. The original v0.1 ZIP and
printable PDFs remain separate, unchanged conversation attachments.

## Your configuration

| Setting | Value |
|---|---|
| Phone | Pixel 9 Pro, Android 17 |
| Recorder | Seeed Studio XIAO nRF54LM20A Sense |
| Hermes Tailscale address | `100.99.200.55` |
| Upload endpoint | `http://100.99.200.55:8765/v1/voice` |
| PlatformIO environment | `seeed-xiao-nrf54lm20a` |
| Zephyr board variant | `xiao_nrf54lm20a/nrf54lm20a/cpuapp` |
| Android build configuration | compile SDK 36, target SDK 36, minimum SDK 33 |

The endpoint is prefilled in Android `Settings.java`. It remains editable during
setup. Android 17 is the intended **device OS**, not a claim that this source was
compiled against SDK 37 or tested on that operating system. The existing SDK 36
configuration is retained rather than making an unverified migration.

`receiver/config.tomstout.json` binds the receiver to your Tailscale IP. The server
bearer token is generated on your own computer during receiver installation and
must be entered once in the app. No token is baked into source or a shared APK.
The receiver config intentionally retains `delivery_mode: "review"` for
commissioning; after checking transcription and the installed Hermes command,
change it to `"auto"` as described in `docs/04-server.md`.

## What blocked binary creation

The build container has a Java compiler and native host compiler, but no Android
SDK, Gradle distribution, PlatformIO, or Seeed/Zephyr cross-toolchain. Attempts to
retrieve the missing SDK files failed; command-line requests could not resolve the
download hosts. SDK download attempts through the separate download tool also
failed. No target compiler invocation completed. This is an environment/access
blocker, not evidence that the application or firmware successfully compiles.

The 56 pre-existing host tests were rerun and passed. They cover portable code and
simulations, not installation, BLE behavior on a Pixel, or real microphone,
flash, regulator, charger, and battery behavior. `TEST_REPORT_v0.2.md` records the
updated checks run on this source update.

## Build on a connected development computer

The supplied build script uses installed vendor build tools. **It does not install
those prerequisites, silently accept software licenses, flash hardware, change
your phone, or start a service on the Hermes computer.** First target builds may
still need source/BSP integration fixes; no successful target build is claimed.

Install these while online:

- Python 3.10 or newer and Git.
- JDK 17 or newer, with `JAVA_HOME` and `PATH` set. JDK 17 is the documented AGP baseline.
- Android SDK Platform 36 and SDK Build-Tools 35.0.0, with `ANDROID_HOME` pointing to the SDK root.
- Gradle 8.11.1, either in `.tools/gradle-8.11.1` or named by `GRADLE_BIN`.
- PlatformIO Core 6.1.16 or newer, accessible as `pio`, or in its normal `~/.platformio/penv` directory.

Seeed's board dependencies are fetched by the first PlatformIO build. Preserve
and pin the installed board package and all toolchain versions after a successful
build; the existing platform URL is still a moving vendor branch, not a lockfile.

On Windows, in PowerShell from the extracted project directory:

```powershell
.\Build-All.ps1 -Check
.\Build-All.ps1
```

On Linux:

```bash
bash build-all.sh --check
bash build-all.sh
```

The script supports `-Target android` / `--target android` and
`-Target firmware` / `--target firmware` for separate builds. Windows may require
your normal approval for running a downloaded PowerShell script. Running
`py -3 tools\build_binaries.py` directly invokes the same builder without changing
your PowerShell execution policy.

### Outputs after a successful build — not present in this ZIP

Each build attempt creates a new timestamped directory in `dist/` and corresponding
compiler logs in `build-logs/`; it never overwrites an older build directory.

Expected Android output: `Hermes-Voice-0.2.0-test.apk`, a **debug-signed personal
test build**, checked with `apksigner verify`. Preserve the development computer's
`~/.android/debug.keystore` for compatible future updates. This is not a
production signing/release process.

Expected firmware outputs:

```text
Hermes-Voice-XIAO-nRF54LM20A-Sense.hex
Hermes-Voice-XIAO-nRF54LM20A-Sense.bin
Hermes-Voice-XIAO-nRF54LM20A-Sense.elf
```

The builder also copies the generated device tree and pairing card, and writes a
build-result JSON file plus SHA-256 checksums. **The private pairing card must not
be published.** It is generated on the development computer, not shared as a
universal password.

An APK is copied only after its compiled manifest, DEX, ZIP integrity, and signing
verification pass. Firmware is copied only after the vendor build, mandatory DTS
preflight, Intel HEX record checks, and ARM ELF checks pass. These are necessary
checks, not proof that the firmware will function correctly on hardware.

If one target fails after another succeeds, `build-result.json` records the
completed stage and the failure. A failed build is never labeled complete.

## First installation and commissioning

Do not try to flash source files, a ZIP, an arbitrary binary, or firmware for a
similarly named nRF54L15/nRF52840 board. Use Seeed's upload workflow for this exact
board after a successful build and inspection of the generated configuration.
Read `docs/02-firmware.md` before flashing.

Start with USB power and the LiPo disconnected. Verify the actual microphone rail,
100 mA charge-current setting, 4.20 V termination, and the physical battery before
battery operation. The static device-tree check is not a physical charger test.
Use the protected 1S LiPo from the original plan. Never cut both live battery
leads at once or bridge them with solder/tools.

On Android, install the resulting test APK, configure the server token, associate
and bond the recorder, then start the relay. Keep Tailscale and Bluetooth enabled.
After a phone reboot, unlock the phone once before testing locked-screen delivery.
This design does not request the phone microphone or replace the default digital
assistant. It remains subject to real-device testing and Android background
restrictions.

The current firmware source uses **System-ON idle**, not the plan's proposed
System-OFF behavior. Battery life and capture latency have not been measured.
No end-to-end delivery or lossless-operation guarantee is made by this source update.

## Reference verification

The following vendor references were checked for the build configuration:

- Android Gradle Plugin 8.10 compatibility: SDK 36 maximum; Gradle 8.11.1, SDK Build-Tools 35.0.0 and JDK 17 defaults:
  https://developer.android.com/build/releases/agp-8-10-0-release-notes
- Android 17 setup and behavior requirements:
  https://developer.android.com/about/versions/17/setup-sdk
  https://developer.android.com/about/versions/17/behavior-changes-17
- Seeed's exact-board PlatformIO instructions:
  https://wiki.seeedstudio.com/xiao_nrf54lm20a_getting_started/
- Vendor board definition (retrieved blob SHA `bf7c2d86b5ca890d8d59b7b0cea1b507e2601459`; this is a file hash, not a platform commit pin):
  https://github.com/Seeed-Studio/platform-seeedboards/blob/main/boards/seeed-xiao-nrf54lm20a.json
- Vendor platform manifest specifies PlatformIO `>=6.1.16` and its nRF54LM20 framework package:
  https://github.com/Seeed-Studio/platform-seeedboards/blob/main/platform.json

A connected build-capable computer is still needed to create and verify the
actual requested binaries. Nothing in this archive is a substitute for that build.
