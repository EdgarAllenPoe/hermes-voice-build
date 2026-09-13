# Hermes Voice Button

Android relay and recorder firmware for the **Seeed Studio XIAO nRF54LM20A Sense**. Not firmware for nRF54L15, nRF52840, or nRF54LM20B.

## Both target builds succeeded

The selected personal test build is [GitHub Actions run 34752997335](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/34752997335), from commit **`073af6bad44e0519368257abc515aa451c4a65f9`**, September 13, 2026. Both Android and firmware compiler/verification jobs completed successfully. Both build records contain `complete: true` and `hardware_tested: false`.

The actual APK and private firmware were downloaded and verified after the build. The encrypted private artifacts were successfully decrypted using the newly backed-up private recovery key. The Android signing-key backup was recovered and its certificate matches the delivered APK. Firmware HEX, BIN and ELF program contents agree, and the pairing card matches its generated configuration.

**These are compiled prototype test binaries, not physically validated releases.** There has been no installation, flashing, microphone, Bluetooth, locked-phone, charger, or battery acceptance test on the user's devices.

See [VERIFIED_BUILD.md](VERIFIED_BUILD.md) for selected file hashes, toolchain details and verification results. See [INSTALL_COMPILED_KIT.md](INSTALL_COMPILED_KIT.md) for setup and private-artifact handling. The repository's earlier “firmware still unresolved” status is superseded by this successful run; earlier build records are preserved.

## Configuration

The Android receiver endpoint is prefilled as `http://100.99.200.55:8765/v1/voice` and remains editable. No server bearer token is baked into the APK. Generate that token on the Hermes host and enter it privately during app setup. Receiver commissioning remains **review-first**; these builds do not deploy or start a receiver or invoke Hermes.

Android baseline: JDK 17, Gradle 8.11.1, AGP 8.10.1, compile/target SDK 36, minimum SDK 33. The delivered compiled manifest confirms package `org.tomstout.hermesvoice`, version 0.2.0 / version code 2. Android 17 is the intended phone OS, not a claim of physical-device testing. This is a debug-signed, debuggable personal app, not a Play Store release.

Seeed PlatformIO distribution pinned to **`1ec1287f8e4bc4067a6fd593991e36875aef989f`**. The working board integration, SCons adapter and BLE compiler fixes are documented in `BOARD_INTEGRATION_NOTES.md`, `SCONS_ADAPTER_FIX.md`, and `BLE_BUILD_FIX.md`. The actual system rail is `vsys_3v3`; the obsolete original `power_en` assumption must not be restored.

## Private material — never publish it unencrypted

This repository is public. Generated BLE passkeys, firmware containing those codes, signing keys, server tokens and recordings must not be committed or uploaded unencrypted.

CI encrypts private firmware/pairing material and Android signing-key backups to `config/build-recipient.crt.pem`. This is a **public certificate only**; its private key is not in the repository or on the runner. The selected run uses the recovery certificate committed in `073af6b`. Older encrypted runs require their own older matching recovery key; changing the current certificate does not decrypt or alter old artifacts.

Each fresh CI build currently creates a new Android test-signing key and recorder passkey. Retain the selected APK with its matching private signing key, and each firmware image with its exact pairing card. For compatible future local Android updates, restore the selected keystore and set `HVB_DEBUG_KEYSTORE` to its absolute path. Do not mix CI builds or uninstall an app holding undelivered recordings merely to resolve a signature mismatch.

## Source and verification scope

This repository contains the target-build subset. The complete private delivery kit restores the original configured v0.2 receiver, documentation and host tests alongside this run's target source. That combined source passed **69 host tests** on September 13, 2026. The kit's separate prebuilt upload helper passed **11 host tests**, without accessing hardware. Independent downloaded-binary checks are recorded in the delivery kit; they are not a security audit or physical acceptance test.

The exact tracked source archived by the two CI jobs is byte-identical. The delivery kit includes it separately for provenance. Original manuals/test reports are preserved as dated historical records; the verified build notes and generated configuration supersede earlier target-build status and board-integration assumptions.

## First commissioning

Start with **USB power and the LiPo disconnected**, the correct board and attached antenna. Verify the generated configuration and measure actual 100 mA / 4.20 V charging behavior before battery operation. The fixed NTC resistor is not a battery-temperature sensor. Follow the full supplied manual's hardware and failure tests.

System-ON idle, 15 committed recorder slots, the 60-second limit, review-first Hermes delivery and Android background limitations remain. Successful compilation does not establish runtime, first-word preservation, safe charging, or end-to-end reliability.
