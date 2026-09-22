# Android 0.5.0 and firmware 0.4.0 verification

Recorded September 22, 2026. Source changes implement the eight improvements described in [the user guide](docs/17-recordings-and-controls.md).

## Delivered components

- Personal Android release: package `org.tomstout.hermesvoice`, version 0.5.0, version code 7, non-debuggable. Signed locally with the retained installation identity and installed as an in-place update on the Pixel 9 Pro XL. Settings, pairing and existing history were retained.
- Firmware 0.4.0: personal XIAO nRF54LM20A Sense, existing pairing identity preserved. Application-only update; no recovery/mass erase, bond reset, or external recording-flash erase performed. All 15 recording slots were free before the update.
- Server: authenticated metadata-only message-status endpoint deployed on the existing receiver, with a timestamped backup of the two original modules. Existing worker/configuration/token/queue preserved. Service restart and authenticated capability check passed.

Signed APK SHA-256: `9541de00f105a9c76f9fd20bab1e3a425f93fb1e085f9b9b5bf4d94aad9a9eba`.

Signing certificate SHA-256: `c9000cf91505d6a88f2a1afc2dc4cd00a507780434040e77fbb1c4a03a8d19d2`.

Private firmware HEX SHA-256: `2ac308ba872f8bfe2f3c43d23d3cb65832428e5d3f7b25644e140efb33019489`.

Firmware images, pairing card and APK are retained in the delivery kit outside the source repository. Private firmware is not published to GitHub. Local logs and screenshots are ignored by Git.

## Automated checks

| Check | Result |
|---|---|
| Pinned Seeed firmware build | Passed; 200,180-byte image footprint; 78,736 bytes RAM reported. |
| Firmware readback | All 200,168 bytes explicitly present in Intel HEX matched direct device readback. Twelve unused alignment bytes between HEX segments are not programmed and were excluded from comparison. No running-CPU RAM verification algorithm used. |
| Android release build and lint | Passed; zero lint errors, 13 warnings. |
| Native codec/capture policy | 12 tests passed, including configurable silence and manual-mode 60-second cap. |
| Flash simulation | 17 tests passed, including exact slot/UUID deletion guards and reboot persistence. |
| Server storage/HTTP | 20 tests passed, including status authentication, malformed/missing IDs, and content-free response. |
| Server worker | 8 tests passed in an isolated test directory with synthetic data. |
| Java protocol/relay | 15 protocol checks and 19 relay simulation scenarios passed. |
| Java dashboard | 27 health scenarios passed. |
| New Java recorder features | Extended/legacy INFO, inventory validation, guarded delete encoding, bad CRC rejection and playback PCM equivalence passed. |
| Android device tests | 24 tests passed in the isolated CI package on the actual phone; queue migrations, retention/deletion, pairing, recorder status, and new screens. |
| Manifest/build-package/prebuilt helper | 3 / 8 / 7 tests passed respectively. |
| Visual checks | Final history and recorder-controls screenshots inspected after correcting text encoding; content fits and scrolls. |

Linux native/server tests ran in an isolated source snapshot, separate from the live server queue. Phone CI tests used a separate application ID. The CI packages and temporary personal-app instrumentation helper were removed after verification.

## Actual device integration

A temporary helper signed with the retained personal key exercised the installed application without clearing its data. Seven assertions passed: fresh authenticated BLE status; a 4-second/manual settings round trip; restoration of the previous settings; active microphone test; advancing microphone frames without a saved recording; microphone stop; and reconnection with fresh inventory.

The recorder reported firmware 0.4.0, MTU 247, zero queued messages, 15 free slots, and no microphone/audio/flash/button-edge errors. The microphone test produced advancing frames and a changing peak level while leaving the recording queue empty. Previous defaults were restored: silence 2 seconds, threshold 40, automatic mode.

The user then made a normal button recording. The phone obtained a matching server receipt, the recorder returned to zero queued / 15 free slots, and the live server reported the new message as `done`. This verifies the recorder-to-phone-to-server-to-Hermes handoff. User-visible Telegram arrival had not yet been confirmed when this report was written. Older processing failures surfaced by the new history API were left intact; tests did not retry or remove them.

The last release adjustment clears a stale “Reconnecting” action label once service discovery succeeds. It changes status text only; the release was rebuilt and linted afterward.

## Remaining acceptance work

These results are not a complete production certification. Still exercise prolonged screen-lock/Doze, out-of-range recovery, queue saturation, interrupted transfer/power, settings persistence across physical power cycles, LED visibility, and a full battery-only/charge cycle. Deletion of real personal recordings was deliberately not used as a hardware test; its logic was tested with synthetic flash/database data. Battery readings are voltage/current diagnostics, not a calibrated percentage or runtime estimate.

The existing hardware workshop DOCX remains a wiring/commissioning reference. For current app controls and LED patterns use the new guide linked above.
