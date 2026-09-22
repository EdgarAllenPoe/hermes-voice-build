# Hermes Voice Button

## Current app and recorder

Android **0.6.0** and firmware **0.5.0** add recording history with server processing stages, confirmed queue controls, reconnect, persistent capture settings, charging diagnostics, distinct LED patterns, phone playback with optional 24-hour retention, and a live microphone test. The speed update adds bounded Bluetooth bursts, recording-ready events, faster English transcription, and overlapping server work. See [speed controls and behavior](docs/18-speed-improvements.md) and [the prompt to give Hermes](docs/HERMES-SPEED-OPTIMIZATION-PROMPT.md). Start with [the current user guide](docs/17-recordings-and-controls.md) and [release verification](SPEED_0.6.0_VERIFIED.md).

The personal app was updated in place on the Pixel 9 Pro XL, preserving its data and signing identity. The firmware was flashed to the personal XIAO nRF54LM20A Sense, with every HEX image byte verified by direct readback and the existing pairing identity retained. Connected status, settings changes/readback, live microphone frames without saved audio, and reconnect were tested on both devices. Extended battery, radio-range, and screen-lock acceptance remain open. The existing dedicated charging LED configuration remains unchanged; see [its commissioning record](docs/16-charging-indicator.md).

The printable workshop manual is [Hermes Voice Hardware Guide](docs/Hermes-Voice-Hardware-Guide-0.3.3.docx), with a [browsable text version](docs/HARDWARE-GUIDE.md). Earlier version reports remain available as historical records; current app behavior is described in the Android guide.

Android relay and recorder firmware for the **Seeed Studio XIAO nRF54LM20A Sense**. Not firmware for nRF54L15, nRF52840, or nRF54LM20B.

## Original v0.2 delivery provenance

The following records the September 13 prehardware delivery. Later commissioning and app releases are described above.

The selected personal test build is [GitHub Actions run 34752997335](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/34752997335), from commit **`073af6bad44e0519368257abc515aa451c4a65f9`**, September 13, 2026. Both Android and firmware compiler/verification jobs completed successfully. Both build records contain `complete: true` and `hardware_tested: false`.

The actual APK and private firmware were downloaded and verified after the build. The encrypted private artifacts were successfully decrypted using the newly backed-up private recovery key. The Android signing-key backup was recovered and its certificate matches the delivered APK. Firmware HEX, BIN and ELF program contents agree, and the pairing card matches its generated configuration.

**At the original September 13 delivery**, these were compiled prototype binaries without physical validation. Subsequent installation and commissioning are recorded above; the historical report does not describe current test coverage.

See [VERIFIED_BUILD.md](VERIFIED_BUILD.md) for selected file hashes, toolchain details and verification results. See [INSTALL_COMPILED_KIT.md](INSTALL_COMPILED_KIT.md) for setup and private-artifact handling. The repository's earlier “firmware still unresolved” status is superseded by this successful run; earlier build records are preserved.

## Configuration

The Android receiver endpoint is prefilled as `http://100.99.200.55:8765/v1/voice` and remains editable. No server bearer token is baked into the APK. Generate that token on the Hermes host and enter it privately during app setup. Receiver commissioning remains **review-first**; these builds do not deploy or start a receiver or invoke Hermes.

Android baseline: JDK 17, Gradle 8.11.1, AGP 8.10.1, compile/target SDK 36, minimum SDK 33. The current personal app package remains org.tomstout.hermesvoice, version 0.6.0 / version code 8. The sideloaded release APK is not debuggable. It retains the original signing key so installation can update the existing app in place. This project does not publish through the Play Store.

Seeed PlatformIO distribution pinned to **`1ec1287f8e4bc4067a6fd593991e36875aef989f`**. The working board integration, SCons adapter and BLE compiler fixes are documented in `BOARD_INTEGRATION_NOTES.md`, `SCONS_ADAPTER_FIX.md`, and `BLE_BUILD_FIX.md`. The actual system rail is `vsys_3v3`; the obsolete original `power_en` assumption must not be restored.

## Private material — never publish it unencrypted

This repository is public. Generated BLE passkeys, firmware containing those codes, signing keys, server tokens and recordings must not be committed or uploaded unencrypted.

CI encrypts private firmware/pairing material and Android signing-key backups to `config/build-recipient.crt.pem`. This is a **public certificate only**; its private key is not in the repository or on the runner. The selected run uses the recovery certificate committed in `073af6b`. Older encrypted runs require their own older matching recovery key; changing the current certificate does not decrypt or alter old artifacts.

CI builds now use a separate disposable Android package (org.tomstout.hermesvoice.ci) and recorder passkey. Personal builds use the retained local signing key and pairing configuration. See docs/10-prehardware.md for restoration. Retain the selected APK with its matching private signing key, and each firmware image with its exact pairing card. For compatible future local Android updates, restore the selected keystore and set `HVB_DEBUG_KEYSTORE` to its absolute path. Do not mix CI builds or uninstall an app holding undelivered recordings merely to resolve a signature mismatch.

## Source and verification scope

This repository now includes the complete Android and firmware source, Linux receiver, hardware notes, synthetic fixtures, build helpers, and host tests restored from the configured v0.2 delivery kit. Compiled binaries and private delivery material remain outside the repository. The automated checks include the host suite, **27 dashboard health scenarios**, **25 recorder relay simulations**, and Android database, pairing, settings, UI and large-text tests on API levels 33, 35 and 36. The original restoration passed 69 tests; these dated results should not be confused with the expanded current suite. The kit's separate prebuilt upload helper passed **11 host tests**, without accessing hardware. Independent downloaded-binary checks are recorded in the delivery kit; they are not a security audit or physical acceptance test.

The exact tracked source archived by the two CI jobs is byte-identical. The delivery kit includes it separately for provenance. Original manuals/test reports are preserved as dated historical records; the verified build notes and generated configuration supersede earlier target-build status and board-integration assumptions.

## First commissioning

Start with **USB power and the LiPo disconnected**, the correct board and attached antenna. Verify the generated configuration and measure actual 100 mA / 4.20 V charging behavior before battery operation. The fixed NTC resistor is not a battery-temperature sensor. Follow the full supplied manual's hardware and failure tests.

System-ON idle, 15 committed recorder slots, the 60-second limit, review-first Hermes delivery and Android background limitations remain. Successful compilation does not establish runtime, first-word preservation, safe charging, or end-to-end reliability.

## Work with the complete source

All commands below run from the Git checkout root (the `source/` folder inside the private delivery kit).

- `receiver/`: Linux HTTP inbox, SQLite queue, Whisper transcription, and review-gated Hermes handoff. Setup: [docs/04-server.md](docs/04-server.md).
- `docs/` and `hardware/`: protocol, setup, commissioning, wiring, and parts notes.
- `tests/` and `fixtures/`: host tests and synthetic audio; no user recordings.
- `tools/`, `Build-All.ps1`, and `build-all.sh`: build, test, and diagnostic entry points.

Run the full host suite on Linux with Python 3.10+, GCC, Git, and JDK 17+:

```sh
bash tools/run_tests.sh
```

GitHub Actions runs this same suite on pushes and pull requests. The worker uses Linux/POSIX process handling and file locks, so the full suite is not a native Windows test suite. Windows remains supported for the Android/firmware build wrappers and prebuilt-kit installation.

Use `python tools/build_binaries.py --check` to inspect installed target tools before building. The repository includes no compiler toolchains, Whisper model, or server bearer token. `receiver/config.tomstout.json` is a preset with placeholder executable paths and a token-file path; generate the actual token on the receiver host as documented.

`SHA256SUMS.txt` records the public source snapshot. Run `python tools/verify_package.py` before modifying it. These source checksums and the private delivery kit's original checksums describe their respective snapshots; later source edits invalidate the old delivery snapshot's source entries without changing its compiled binaries.

## Pairing configuration and Git

`firmware/src/device_config.example.h` is the public template. The real `firmware/src/device_config.h` is generated locally by `python tools/provision.py`, is ignored, and is no longer tracked. The existing local header was preserved during this source synchronization; no real passkey or signing key was rotated. If another checkout already has a provisioned header, back up that header and its matching card before pulling the change that removes the tracked header, then restore the header as an ignored local file if necessary.

A fresh checkout is provisioned automatically by the firmware build helper, or explicitly with `python tools/provision.py` before running PlatformIO directly. Keep the generated header with `config/private/pairing-card.txt`. Provisioning refuses to overwrite an existing configuration without `--force`; that option intentionally rotates the recorder code.

The ignore rules also cover copied firmware/APK outputs, private key files, environment files, receiver databases, and real recordings. The public build-recipient certificate and the two named synthetic audio fixtures are explicitly allowed. Git ignore rules do not undo previous publication and can be bypassed with force-add, so review staged files before pushing.

After reviewing and staging source changes, run python tools/update_snapshot.py, then stage SHA256SUMS.txt. This refreshes the public snapshot without including ignored private files.
