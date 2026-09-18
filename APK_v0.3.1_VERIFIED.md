# Verified personal APK 0.3.1

Built and locally signed September 18, 2026. This update fixes the invalid Bluetooth address error during companion pairing. It normalizes the address before use and repairs lowercase addresses retained by version 0.3.0.

| Item | Verified value |
|---|---|
| Delivery file | android/Hermes-Voice-0.3.1-test.apk |
| Package | org.tomstout.hermesvoice |
| Version name / code | 0.3.1 / 4 |
| Minimum / target Android SDK | 33 / 36 |
| APK SHA-256 | 381c2d5401eb7e40d6e4cc2a32fb5075f8c1c4964d780302e27a0e37d82a0195 |
| Signer certificate SHA-256 | c9000cf91505d6a88f2a1afc2dc4cd00a507780434040e77fbb1c4a03a8d19d2 |
| Compiled source commit | 0220e145547deaed37d201b943c1e43dddfc82d7 |
| Personal build | [35368969752](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/35368969752) |
| Android emulator tests | [35368969582](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/35368969582) |

The personal package compiled and passed Android lint. The artifact archive digest and individual checksums were verified before local signing. The retained original key signed the APK locally; no private signing key was sent to GitHub. Google's apksig verifier confirmed the signature and its match with the existing personal 0.3.0 APK. Independent verification checked the signature, content digest, package/version, DEX checksums and tamper rejection. All unsigned archive entries retained identical contents after signing.

All 11 Android 15 emulator tests passed, including five address regression cases using Android's real MacAddress and BluetoothAdapter classes. The source host-test workflow also passed. These tests reproduce the address-formatting failure and confirm the correction; physical pairing and audio transfer on the user's phone still need confirmation.

Install this APK as an update over the existing personal app. Do not uninstall or clear app data. Open Hermes Voice, hold the board's B button for about two seconds, release, and tap Pair voice button. Complete the Android pairing prompt using the existing private code if requested. The recorder remains on the verified 0.3.0 firmware; no reflash or bond reset is needed for this fix.

Local verification records: verification/apk-v0.3.1-build-result.json and verification/apk-v0.3.1-independent-verification.json in the delivery kit.
