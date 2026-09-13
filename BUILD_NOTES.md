# Build provenance and privacy

The Android and firmware sources and original build helpers come from Hermes-Voice-Button-Configured-Source-v0.2.zip. The original archive's 106-file SHA-256 manifest was verified and its 62 host tests passed before upload. Those tests are not target compilation or hardware acceptance tests.

For the first cloud build, the Seeed PlatformIO platform was pinned to commit `1ec1287f8e4bc4067a6fd593991e36875aef989f`. The original source followed the moving main branch. Actual package versions and the generated device tree are retained by the build.

CI additions: `.github/workflows/build.yml`, `tools/ci_package.py`, and `config/build-recipient.crt.pem`. Only the public encryption certificate is committed. The matching private decryption key is retained outside GitHub and must be backed up privately.

Android APK output is debug-signed, intended for personal testing, and requires installation and runtime testing on the actual phone. Each fresh runner generates a different debug signing key; retain the encrypted key backup for future compatible updates. Do not uninstall the application while it contains undelivered recordings.

Firmware and its generated pairing code are encrypted together before artifact upload. Do not publish the decrypted firmware because its private passkey is embedded in the program. Each fresh build provisions a new passkey; use the card delivered with that exact firmware. Firmware artifacts are collected as successful only after compilation and the original static DTS/HEX/ARM ELF checks pass.

Use USB power first with the LiPo disconnected. Verify the exact nRF54LM20A Sense board, actual microphone rail, flash/settings boundaries, and 100 mA / 4.20 V charging behavior before battery operation. This is not firmware for an nRF54L15 or nRF52840.

The repository now includes the Linux receiver, Markdown guides, hardware notes, fixtures, and host tests. The original printable manual and compiled binaries remain in the separate private delivery kit. Committing or building this source does not deploy or start services on the Hermes host.
