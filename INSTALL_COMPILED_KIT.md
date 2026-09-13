# Installing the compiled personal test kit

Selected build: [run 34752997335](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/34752997335), source `073af6bad44e0519368257abc515aa451c4a65f9`. See [VERIFIED_BUILD.md](VERIFIED_BUILD.md) for actual hashes and verification limits.

## Private download package

The privately delivered ZIP includes the APK, exact-board HEX/BIN/ELF, matching pairing card, generated DTS/Kconfig, restored complete source and receiver, original manual, build logs, and a prebuilt-image upload helper. Extract it completely and open **START-HERE.html** or **START-HERE.md** at its root. The separate PRIVATE Keys and Pairing ZIP holds the matching Android signing key and recovery key; retain it securely.

These ZIPs are not password-protected. Do not upload either private package, the firmware, pairing card, generated header, or private keys to this public repository. The firmware itself contains its six-digit passkey.

## Android

Open `android/Hermes-Voice-0.2.0-test.apk` on the phone and use Android's normal installation approval. Do not disable unrelated security protections. This is a debug-signed, debuggable personal prototype. A different CI run may have a different signing key; do not uninstall an older copy that still holds undelivered recordings.

The receiver endpoint is prefilled as `http://100.99.200.55:8765/v1/voice`. Generate the server bearer token on the Hermes computer and enter it privately; the APK does not contain it. The Bluetooth passkey is a separate secret, not the server token.

## Exact-board firmware

Use only **Seeed Studio XIAO nRF54LM20A Sense**, with its antenna, a USB data cable, and the **LiPo disconnected**. Preserve valuable previous board data first: this application claims external flash for its recording/settings regions.

The full private kit's `tools/flash_prebuilt.py` defaults to a read-only check. It needs Python 3.10+. From the extracted kit root on Windows:

```powershell
py -3 tools/flash_prebuilt.py
```

For actual uploading, install Git, PlatformIO 6.1.18, Seeed platform revision `1ec1287f8e4bc4067a6fd593991e36875aef989f`, and the vendor-selected `platformio/tool-openocd@~3.1200.0` package using the explicit commands in START-HERE. Then run:

```powershell
py -3 tools/flash_prebuilt.py --flash
```

The helper requires an interactive typed confirmation. It checks the delivered firmware hashes and generated configuration, validates the exact vendor board/loader files, loads the delivered HEX with `nrf54lm20a-load`, verifies it with `verify_image`, and resets. It does not compile or rotate a pairing code. It disables the vendor's automatic mass-erase recovery hook before connection; a locked/unrecognized board requires deliberate diagnosis. The helper passed 11 host checks, but its actual USB upload has not been physically tested here. Linux instructions are in START-HERE.

Do not flash a ZIP, drag files to a guessed USB drive, select a similarly named board, or use a source-build command as though it merely installs this prebuilt image. Stop on checksum/configuration/connection/write/verification errors.

## Commissioning

Set up the restored Linux receiver under `source/receiver/`, following `source/docs/04-server.md`. The build did not install it, start services, configure Whisper/Hermes, or connect to the user's host. Start in review mode and use harmless test recordings.

Grant the app's Nearby devices/Bluetooth and notification permissions. Save the URL/token, open the recorder's pairing window with a 1.5-second hold, use Pair voice button, and complete the separate Bluetooth bond with `private/pairing-card.txt`. Start relay and keep Tailscale/Bluetooth on. Unlock the phone once after reboot. One vibration means phone receipt; two short vibrations mean server acceptance, not completed Hermes processing.

Complete USB-only microphone/flash/BLE tests, then the original manual's physical charging checks before battery operation. The generated configuration checks 100 mA / 4.20 V, but static checks are not measurements. The fixed thermistor resistor does not measure battery temperature. Runtime, first-word capture, Android 17 locked-screen behavior, and end-to-end delivery remain untested.

The original v0.1 manual is historical where it says no target binaries exist or assumes `power_en`. The compiled pinned integration uses `vsys_3v3` and `dmic_vdd`; see BOARD_INTEGRATION_NOTES.md. Physical safety and failure-testing requirements remain applicable.

## Recovering the CI private artifacts

The selected run's firmware artifact includes `PRIVATE-firmware.cms`; its Android artifact includes `PRIVATE-android.cms`. Decrypt them only locally with the matching privately delivered key and public certificate. Example, from the private backup directory:

```bash
openssl cms -decrypt -binary -inform DER -in /path/to/PRIVATE-firmware.cms -recip recovery/build-recipient.crt.pem -inkey recovery/build-recipient.key.pem -out recovered-firmware.zip
```

Changing the current repository certificate does not decrypt older runs. Preserve this selected signing key and pairing card rather than mixing builds. Set `HVB_DEBUG_KEYSTORE` to the recovered keystore's absolute path for compatible future local Android builds. Never commit the private recovery key to enable a workflow.

Vendor references: [exact-board workflow](https://wiki.seeedstudio.com/xiao_nrf54lm20a_getting_started/), [pinned loader](https://github.com/Seeed-Studio/platform-seeedboards/blob/1ec1287f8e4bc4067a6fd593991e36875aef989f/builder/board_build/nrf/nrf_build.py), and [pinned target configuration](https://github.com/Seeed-Studio/platform-seeedboards/blob/1ec1287f8e4bc4067a6fd593991e36875aef989f/builder/board_build/nrf/nrf54lm20a.cfg).
