# Verified personal APK 0.3.0

Superseded by [personal APK 0.3.1](APK_v0.3.1_VERIFIED.md), which fixes the Bluetooth address error during pairing.

Built and locally signed September 14, 2026. This supersedes the earlier statement that only a CI version 0.3 APK existed. The original version 0.2 APK and firmware are preserved.

| Item | Verified value |
|---|---|
| File in the private delivery kit | android/Hermes-Voice-0.3.0-test.apk |
| Package | org.tomstout.hermesvoice |
| Version name / code | 0.3.0 / 3 |
| Minimum / target Android SDK | 33 / 36 |
| Size | 86,591 bytes |
| APK SHA-256 | f911ad92660db9ef6cf089391ad8900ebf81648fb0a45f7875c1ca7cb26ef38e |
| Signer certificate SHA-256 | c9000cf91505d6a88f2a1afc2dc4cd00a507780434040e77fbb1c4a03a8d19d2 |
| Compiled source commit | f32b839b9f74207081f711ceab91e52e268b8ba6 |
| Build run | [34874910387](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/34874910387) |

The build compiled the personal package and passed Android lint. GitHub received no personal signing key and exported an unsigned APK. Its artifact digest and contained file checksums were verified before local signing. The unsigned workflow artifact is not the final installable APK.

The final APK was signed locally with the retained original key using Google's apksig 8.10.1. Google's verifier accepted the result, and the signing certificate matched the actual original version 0.2 APK. An independent verifier also checked the RSA/SHA-256 v2 signature, APK content digest, binary manifest, DEX checksums, modified-content rejection and truncation rejection. Every ZIP entry from the unsigned APK retained identical content after signing.

The source suite passed 121 host tests and six Android emulator database tests after the build-mode addition. Installation on the user's phone and physical recorder tests have not occurred. This remains a debug-signed personal prototype, not a Play Store release.

The delivery kit also contains android/Hermes-Voice-0.3.0-test.apk.sha256, verification/apk-v0.3-build-result.json and verification/apk-v0.3-independent-verification.json. These files describe the new APK without replacing the original delivery's records.

For an existing personal version 0.2 installation, use the normal Android update/install process with this APK. The package and signing identity match and the version code is higher. Do not uninstall an app containing pending recordings to troubleshoot an update. This build does not replace the separate .ci test package.

Future builds: [personal APK compilation and local signing](docs/11-personal-apk.md). No new personal firmware was built or flashed for this APK request.
