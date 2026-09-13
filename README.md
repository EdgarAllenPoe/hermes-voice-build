# Hermes Voice Button

Android relay application and recorder firmware for the **Seeed Studio XIAO nRF54LM20A Sense**. This is not firmware for an nRF54L15, nRF52840, or nRF54LM20B.

## Build status

The Android v0.2.0 personal test APK was successfully compiled and checked in [run 34737065407](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/34737065407), from commit `40c370138248ceac6be09ee5f90722b89a1f2a58`. Verification covered the compiled manifest, DEX, archive integrity, and APK signature. Its private signing-key backup was recovered and its certificate matched the APK.

Selected APK SHA-256:
`83ce48b848026e78ad28f5bd7e7596193885a9160c72053dafb4e7db263cb27f`

Selected signer certificate SHA-256:
`f43941a324f58881afec95ded26e715e1b5071b33cd8a930ef68d4cafd1f3f2a`

**Firmware compilation is still being resolved.** The generated DTS and Kconfig from an actual build attempt passed the strengthened static checks, but an unsuccessful compiler invocation is not a firmware release. Review the latest workflow's `build-result.json`; only a result with `complete: true` and the firmware verification stage establishes a completed target build. A green artifact-upload step alone does not.

The original configured source package passed 62 host tests. The working full-source copy subsequently passed 69 tests after adding seven mocked tests for the narrow PlatformIO adapter patch. Host tests are not Android installation, target compilation, or board acceptance tests. This repository initially contains the target-build subset; the full receiver, original test suite, and manual remain in the supplied complete project package.

## Configuration

The Android endpoint is prefilled as `http://100.99.200.55:8765/v1/voice` and can be changed during setup. No server token is built into the APK. Generate the token on the Hermes host and enter it privately in the app. The receiver remains in review-first commissioning mode; these workflows do not deploy or start it.

Build baseline: JDK 17, Gradle 8.11.1, AGP 8.10.1, Android compile/target SDK 36, minimum SDK 33. Android 17 is the intended phone operating system, not a claim of physical-device testing.

The Seeed PlatformIO distribution is pinned to `1ec1287f8e4bc4067a6fd593991e36875aef989f`. See `BOARD_INTEGRATION_NOTES.md` and `SCONS_ADAPTER_FIX.md` for changes required by the actual vendor build.

## Private build material

This repository is public. Generated BLE pairing codes, firmware containing those codes, Android signing keys, server tokens, and recordings must not be committed or published unencrypted.

CI encrypts private firmware/pairing material and the signing-key backup to `config/build-recipient.crt.pem`. That file is a **public encryption certificate**, not the private decryption key. Keep the separately delivered private backup secure. Never upload the private key to this repository.

Each fresh CI build currently creates a new test-signing key and a new recorder passkey. Keep the selected APK with its matching signing key, and each firmware image with its exact pairing card. For compatible Android updates, restore the selected private key locally and set `HVB_DEBUG_KEYSTORE` to its absolute path. Do not replace a working APK with an independently signed CI build or uninstall an app containing undelivered recordings.

## Hardware commissioning

Neither the APK nor the board has been installed or physically tested by these workflows. Start with **USB power and the LiPo disconnected**. Follow the supplied manual and exact-board vendor upload instructions. Verify the generated configuration and measure actual 100 mA / 4.20 V charging behavior before attaching or relying on the battery.

A successful compilation does not establish working Bluetooth, microphone capture, locked-screen delivery, charging safety, or battery runtime. The prototype uses System-ON idle and retains the original commissioning and retention limits.
