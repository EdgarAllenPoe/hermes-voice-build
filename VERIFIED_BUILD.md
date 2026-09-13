# Verified binary build — September 13, 2026

## Selected run

[GitHub Actions run 34752997335](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/34752997335), attempt 1, compiled source commit `073af6bad44e0519368257abc515aa451c4a65f9`.

Android job `103712572541` and firmware job `103712572658` both completed successfully. Both build-result records contain `complete: true` and `hardware_tested: false`.

Artifacts: `hermes-android-34752997335-1` (ID `10316732182`) and `hermes-firmware-34752997335-1` (ID `10315673889`). Both were downloaded; their outer GitHub SHA-256 digests and internal manifests were verified. Their private CMS payloads were successfully decrypted using the newly backed-up recovery key. The source archives from the two matrix jobs were byte-identical.

The unencrypted firmware and pairing material are delivered privately, not published to this public repository.

## Actual file hashes

| File | SHA-256 |
|---|---|
| Hermes-Voice-0.2.0-test.apk | `ceb1267f61de189297f3f78dd61f68ec1c29a5ed1a9eec27fbe25b3560d6d5c1` |
| Hermes-Voice-XIAO-nRF54LM20A-Sense.hex | `b3b8fe5a55d7f53b64e1768d84e9505691091f86b17e54ac1249379f0377860f` |
| Hermes-Voice-XIAO-nRF54LM20A-Sense.bin | `807a4c56ed2bec1ac9e219c53f963051a50dee28e2c60d6520a6999468772932` |
| Hermes-Voice-XIAO-nRF54LM20A-Sense.elf | `dcfc0ce765e80881bc3a2453eb824e71253d5bc91de9a9ba10a6d679a6de0df1` |

APK signer certificate SHA-256: `c9000cf91505d6a88f2a1afc2dc4cd00a507780434040e77fbb1c4a03a8d19d2`.

Artifact ZIP SHA-256: Android `d6b6efa6283adfa84b266c4af86f98ba20ba112ff3ffa71bffa133dabede54f9`; firmware `d719286fa56c077a962a7c01590721f7a234d3728af59211f45e49e20d62168a`.

## Verification performed

**Android:** The real Gradle/Android SDK build completed. Google's `apksigner verify --verbose --print-certs` passed using APK Signature Scheme v2. After download, a separate narrow verifier rechecked the RSA/SHA-256 signature and protected content digest, ZIP integrity, DEX checksums and compiled manifest. Modified-content and truncated-APK negative checks were rejected. The certificate exported from the recovered private signing keystore matches this exact APK's signer. The additional verifier is not a replacement for Android's verifier or a security audit.

The compiled manifest confirms package `org.tomstout.hermesvoice`, version `0.2.0`, version code `2`, compile/target SDK `36`, minimum SDK `33`, debuggable `true`, and backup disabled. The app does not request `RECORD_AUDIO` or replace the phone's assistant. Intended phone OS: Android 17; not physically tested.

**Firmware:** Real PlatformIO/Zephyr ARM compilation and linking completed for `seeed-xiao-nrf54lm20a`, variant `xiao_nrf54lm20a/nrf54lm20a/cpuapp`. A raw BIN was generated from the linked ELF using the installed ARM objcopy. Intel HEX record checksums passed. Every addressed HEX program byte agrees with the BIN; file-backed ELF program load segments agree too. The BIN spans `0x0` through `0x2ff3f`, 196416 bytes; the HEX carries 196408 data bytes. ELF is ARM32 little-endian. The privately inspected pairing card, generated header and compiled pairing constant agree; no pairing code is published here.

The actual generated DTS and Kconfig passed a fresh local recheck: 100 mA charge current, 4.20 V termination, 3.3 V system/microphone rails, separate audio/settings partitions, P1.00 input, required secure RNG/BLE configuration, single connection/bond, and charger-before-regulator initialization.

**Host tests:** The restored configured v0.2 complete source with this run's target-build updates passed **69 host tests**. The separately packaged prebuilt-image helper passed **11 host tests**, including read-only default operation, checksum/path rejection, verification command, disabled auto-mass-erase hook, cancellation, and noninteractive refusal. Those helper tests used mocks or local files, not a USB board. Logs and independent verification records are in the private delivery kit.

## Toolchain and warnings

Android: JDK 17, Gradle 8.11.1, AGP 8.10.1, SDK 36, Build Tools 35.0.0. Firmware: PlatformIO 6.1.18; Seeed platform commit `1ec1287f8e4bc4067a6fd593991e36875aef989f`; Zephyr package `3.40400.260428`. Full resolved packages are recorded in the compiler logs. The platform revision is pinned; not every transitive Python/build dependency is locked.

Warnings were not suppressed to manufacture success. Retained logs include deprecated `bt_passkey_set`, vendor/libc `noreturn`, and SPI timing-property deprecation warnings. These remain maintenance items, not evidence of runtime correctness.

## Not tested or deployed

No physical flash/boot, microphone, first-word timing, BLE transfer, Android installation/background wake, Tailscale routing to the user's machine, real transcription/Hermes handoff, charge-current/termination measurement, battery temperature/runtime, or enclosure tests have been performed. No server was installed or started. Successful compilation and static verification are not hardware acceptance.

The selected private recovery key and signing-key backup were delivered outside GitHub. Each fresh CI run currently creates a new signing key/passkey; preserve the selected set rather than mixing runs. The original manual's source-only status and `power_en` description are historical; the updated integration uses `vsys_3v3` and `dmic_vdd`.
