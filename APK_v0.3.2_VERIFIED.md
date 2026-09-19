# Verified personal APK 0.3.2

Built and locally signed September 19, 2026. This update fixes the recorder queue count remaining at 1 or 15 after delivery. The displayed count decreases after each confirmed recorder acknowledgement, followed by a fresh INFO read. An immediate disconnect after the final acknowledgement retains the adjusted count as an estimate. Recorder details include their last-read time and are labelled last known.

| Item | Verified value |
|---|---|
| Delivery file | android/Hermes-Voice-0.3.2-test.apk |
| Package | org.tomstout.hermesvoice |
| Version name / code | 0.3.2 / 5 |
| Minimum / target Android SDK | 33 / 36 |
| APK SHA-256 | 9d89688a90ecc5c4bd9aca8b2d554c49ab80727b416101b1bedac6141eae8ee8 |
| Signer certificate SHA-256 | c9000cf91505d6a88f2a1afc2dc4cd00a507780434040e77fbb1c4a03a8d19d2 |
| Compiled source commit | f70d8abc1f9efbfc434a0c1d25b60c939857751f |
| Personal build | [35451711870](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/35451711870) |
| Android emulator tests | [35451711893](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/35451711893) |
| Host tests | [35451711863](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/35451711863) |

The personal APK compiled and passed Android lint. Artifact and file checksums were verified before signing locally with the retained key. Google's apksig verifier confirmed the signature and its match with personal APK 0.3.1. Independent checks verified the manifest, DEX checksums, signature, content digest, tamper rejection and unchanged unsigned archive contents.

All 15 Android 15 emulator tests passed, including four new status-persistence regressions. The host suite passed, including 19 relay simulation scenarios. These cover a full recorder queue, immediate disconnect after the final ACK, a new recording during transfer, lost ACK responses, interrupted transfers, failed phone storage, corrupt recordings skipped without deletion, and legacy/missing INFO support. Physical Bluetooth behavior with the updated app still needs confirmation on the user's phone.

## Install and check

1. Install the personal 0.3.2 APK as an update over the existing app. Keep its data; do not uninstall first.
2. Open Hermes Voice and tap Start relay if needed.
3. With the board powered over USB, hold B for about two seconds and release. This opens a connection window so the app can replace its old saved snapshot. Existing pairing remains valid; no new pairing or firmware flash is required.
4. Tap Refresh status. An empty board should report Recorder queue: 0.
5. Make one short recording and allow transfer. Refresh status again. The queue should be zero, possibly labelled estimated after confirmed transfer if the board disconnected immediately.
6. Check the phone queue and Last upload separately for server acceptance.

Local verification records are verification/apk-v0.3.2-build-result.json and verification/apk-v0.3.2-independent-verification.json in the delivery kit. The firmware remains at the commissioned 0.3.1 version.
