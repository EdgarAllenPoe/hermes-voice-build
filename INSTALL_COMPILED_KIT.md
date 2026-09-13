# Hermes Voice Button — compiled personal test kit

> Paths and commands in this guide are relative to the extracted **private delivery kit**, not to a GitHub source checkout. The kit contains the APK, firmware, pairing card, and prebuilt-image helper; those delivery files are not part of this public repository. For building from source, start with [README.md](README.md).

**The Android APK and exact-board firmware have both been compiled.** This kit contains real binaries from GitHub Actions run **34752997335**, source commit **073af6bad44e0519368257abc515aa451c4a65f9**, built September 13, 2026. They are not placeholders or renamed source files.

**Hardware acceptance is still outstanding.** Neither this APK nor this firmware has been installed or tested on your physical phone/recorder here. Keep the battery disconnected for first installation and USB-only commissioning. Do not rely on this prototype for important recordings until the manual's acceptance tests pass.

## What to open

| Item | Location in this kit |
|---|---|
| Android app | `android/Hermes-Voice-0.2.0-test.apk` |
| Preferred firmware image for the supplied uploader | `firmware/Hermes-Voice-XIAO-nRF54LM20A-Sense.hex` |
| Alternate/debug firmware formats | Matching `.bin` and `.elf` in `firmware/` |
| Your matching private Bluetooth pairing card | `private/pairing-card.txt` |
| Generated hardware configuration | `firmware/generated-zephyr.dts` and `generated-zephyr.config` |
| USB prebuilt-image check/upload helper | `tools/flash_prebuilt.py` |
| Full project source, receiver and original tests | `source/` |
| Original printable manual, preserved as historical v0.1 | `reference/Hermes-Voice-Button-Manual-v0.1.pdf` |
| Build records, independent verification and test logs | `verification/` |

**This entire kit is private:** the firmware contains your device's pairing code, and the pairing card and generated header are included. Do not upload the kit, firmware, or `private/` folder to a public repository. The separate **PRIVATE Keys and Pairing** ZIP contains your Android signing key and recovery key; save that securely too. The ZIP files are not password-protected.

## 1. Install the APK on Android

Open `Hermes-Voice-0.2.0-test.apk` on the phone and follow Android's normal installation approval. Allow installation from the particular file-opening app only when Android asks. Do not disable Play Protect or other unrelated protections.

This is a **debug-signed, debuggable personal test app**, version 0.2.0, package `org.tomstout.hermesvoice`. Its actual compiled manifest uses minimum SDK 33 and target/compile SDK 36. Android 17 is the intended phone OS; locked-screen behavior on that OS remains untested.

The signing certificate is new for this selected run. An APK installed from a different CI run may use a different signing key. **Do not uninstall an older copy while it holds undelivered recordings.** Preserve/reconcile those recordings first. The recovered signing-key backup matches this exact APK and allows future local updates to retain its signing identity.

The receiver URL is prefilled as:

```text
http://100.99.200.55:8765/v1/voice
```

The server bearer token is intentionally **not** inside the APK. Generate it on your Hermes computer during receiver setup and enter it privately in the app. The Bluetooth pairing code is not the server token. The app uses the recorder's microphone, not the phone microphone, and does not replace your default assistant.

## 2. Prepare the computer for flashing a prebuilt image

Confirm the board label is **Seeed Studio XIAO nRF54LM20A Sense**. These images are not for nRF54L15, nRF52840, or nRF54LM20B. Attach its antenna. Leave the protected LiPo disconnected. Back up any valuable data from earlier board firmware; this application claims the external recording/settings flash regions on first use.

Extract the whole kit to a normal folder before running anything. Do not run files directly inside a ZIP. Use only the one intended board/debug probe during flashing. The computer must recognize its USB/CMSIS-DAP interface, and the USB cable must carry data.

You need Python 3.10 or newer, Git, PlatformIO, and the exact vendor OpenOCD/configuration files. **No Android SDK, Gradle, or target recompilation is needed to install these prebuilt binaries.** The provided helper does not install tools or drivers automatically.

For Windows, after installing Python and Git, use PowerShell:

```powershell
py -3 -m pip install "platformio==6.1.18"
py -3 -m platformio pkg install --global --platform "https://github.com/Seeed-Studio/platform-seeedboards.git#1ec1287f8e4bc4067a6fd593991e36875aef989f"
py -3 -m platformio pkg install --global --tool "platformio/tool-openocd@~3.1200.0"
```

For Linux, use a Python virtual environment and the equivalent commands:

```bash
python3 -m venv .hvb-tools
. .hvb-tools/bin/activate
python -m pip install 'platformio==6.1.18'
python -m platformio pkg install --global --platform 'https://github.com/Seeed-Studio/platform-seeedboards.git#1ec1287f8e4bc4067a6fd593991e36875aef989f'
python -m platformio pkg install --global --tool 'platformio/tool-openocd@~3.1200.0'
```

These explicit commands download vendor packages and need Internet access. Follow the normal OS/vendor USB permission or driver setup when needed; do not run a guessed recovery/mass-erase procedure or substitute another board target.

The version/path choices follow the pinned Seeed board manifest and OpenOCD loader [1–3]. Actual tool installation and USB flashing on your workstation have not been tested here.

## 3. Check, then flash

Open a terminal **in the extracted kit folder**. The first command is safe to run without connecting a board:

```powershell
py -3 tools/flash_prebuilt.py
```

On Linux use `python tools/flash_prebuilt.py`. Default mode verifies the firmware checksums and generated configuration, and **does not access hardware**.

After checking the exact board, USB data cable, antenna, disconnected battery, and previous-data backup, explicitly request flashing:

```powershell
py -3 tools/flash_prebuilt.py --flash
```

On Linux use `python tools/flash_prebuilt.py --flash`. The helper asks you to type:

```text
FLASH XIAO nRF54LM20A SENSE
```

It uses the vendor's `nrf54lm20a-load`, then `verify_image`, then reset. It does not rebuild firmware or change the pairing code. As an extra precaution, this helper disables the vendor's automatic mass-erase recovery hook before opening the connection. A locked/unrecognized device must therefore be diagnosed deliberately rather than automatically erased. This helper's checks and confirmation behavior passed 11 host tests; **its physical upload operation is not tested**.

Nonstandard installations can be supplied with `--core-dir`, `--platform`, or `--openocd-package`; run `--help` for details. The helper verifies the board/configuration fingerprints and rejects an incompatible OpenOCD package. Stop on a failed checksum, configuration check, connection, write, or verification.

Do not drag these files onto a guessed USB drive, flash a ZIP, or rename a BIN to HEX/UF2. Do not use the ordinary source-build upload command merely to install this kit: it can recompile or produce a differently provisioned image. The helper specifically loads the delivered HEX.

## 4. Set up the receiver and pair the phone

The Linux receiver and instructions are restored under `source/receiver/` and `source/docs/04-server.md`. They have **not** been installed or started on your Hermes computer by this build. Your host OS, absolute Hermes executable/model paths, and desired notes destination still need local confirmation.

Start the receiver in **review** mode and verify a harmless upload before enabling automatic Hermes actions. Follow the server guide for token creation, Tailscale binding, Whisper and the checked Hermes `chat --query-file` adapter. Keep Tailscale running on both endpoints. No public port forwarding or Funnel is needed.

In the app, grant Nearby devices/Bluetooth and notification permissions, save the receiver URL/token, hold the recorder button for about 1.5 seconds to open its pairing window, and tap **Pair voice button**. Approve Android's association and separate Bluetooth bond using `private/pairing-card.txt`. Then tap **Start relay**.

Double-click the recorder and speak. A single click while recording saves manually; automatic silence stop remains the prototype's simple energy-based gate. One phone vibration indicates durable phone receipt; two short vibrations indicate server acceptance, not completed Hermes processing. Keep harmless test commands in review mode. Unlock once after a phone reboot; force-stopping the app requires reopening it.

## 5. Commission the hardware before battery operation

The actual generated DTS/Kconfig passed static checks for **100 mA charging, 4.20 V termination, 3.3 V rails, the P1.00 button, recording/settings partition ranges, secure RNG/BLE settings, and charger-before-regulator initialization**. These checks do not measure the real charger, microphone, battery temperature, current draw, or radio.

With USB power only, establish microphone capture, correct first-word capture, safe queue behavior, and BLE transfer. Before battery use, follow the original hardware guide to verify polarity, protected-cell suitability and physical charging behavior. The selected two-wire cell has no thermistor; the fixed NTC resistor is not cell-temperature measurement. Do not solder an energized battery or perform short/deep-discharge tests.

Then test locked-screen relay, a phone idle for 30 minutes, Bluetooth/Tailscale/server outages, reconnects, reboot after first unlock, queue-full behavior and repeated captures. No battery-life claim or lossless-operation guarantee has been established. Firmware remains **System-ON idle**, with at most 15 committed recordings and a 60-second message limit.

### Important correction to the original printed manual

The v0.1 manual is retained unchanged for reference, but its target-build status and `power_en` integration description are historical. The successfully compiled pinned vendor package uses **`vsys_3v3` (BUCK2) for the real system rail** and **`dmic_vdd` (LDO1) for microphone power**. `BOARD_INTEGRATION_NOTES.md` in `source/` documents the changes. Do not restore the obsolete `power_en` assumption. The generated configuration and updated source in this kit supersede those original integration details, not the manual's physical safety checks.

## Backups and reproducibility

Keep this kit, its matching private backup ZIP, and the pairing card together securely. Each fresh CI run currently generates a new recorder passkey and Android test-signing key. Do not mix firmware and pairing cards from different runs.

For a later local build, restore the selected keystore and set `HVB_DEBUG_KEYSTORE` to its absolute path. To retain this recorder's passkey, copy the private generated header and card into the corresponding local source locations before rebuilding; never commit them. Compiler/toolchain downloads are separate from source, so this kit is not a certified offline development environment.

`verification/exact-target-source-for-run.zip` is the byte-identical tracked source returned by both CI jobs. `source/` combines that target source with the original configured v0.2 receiver, documentation and tests. The restored/updated source passed **69 host tests**. Historical reports under `source/` describe their original dates, not this run. The CI logs and independent verification records are in `verification/`.

## Primary references

[1] Seeed exact-board PlatformIO/USB workflow: https://wiki.seeedstudio.com/xiao_nrf54lm20a_getting_started/

[2] Pinned board manifest and OpenOCD package selection: https://github.com/Seeed-Studio/platform-seeedboards/blob/1ec1287f8e4bc4067a6fd593991e36875aef989f/boards/seeed-xiao-nrf54lm20a.json and https://github.com/Seeed-Studio/platform-seeedboards/blob/1ec1287f8e4bc4067a6fd593991e36875aef989f/platform.json

[3] Pinned uploader/target configuration: https://github.com/Seeed-Studio/platform-seeedboards/blob/1ec1287f8e4bc4067a6fd593991e36875aef989f/builder/board_build/nrf/nrf_build.py and https://github.com/Seeed-Studio/platform-seeedboards/blob/1ec1287f8e4bc4067a6fd593991e36875aef989f/builder/board_build/nrf/nrf54lm20a.cfg

[4] APK signature format used for the independent signature/content recheck: https://source.android.com/docs/security/features/apksigning/v2

[5] Actual target build: https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/34752997335
