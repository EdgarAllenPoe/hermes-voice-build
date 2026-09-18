# Microphone commissioning and firmware 0.3.1

On September 18, 2026, a XIAO nRF54LM20A Sense connected to the personal Android app 0.3.1 recorded and delivered a 2.28-second manual test. The receiver produced a numeric transcript and held it in review. The test did not invoke Hermes.

## Why automatic recording stopped early

Five preceding attempts each accumulated 250 audio frames (five seconds) and were discarded by the no-speech timeout. The board reported no microphone-start, audio-read, dropped-button-edge, or flash errors.

The default detection threshold of 300 was too high for the measured microphone output. In the decoded manual recording, the per-frame mean absolute deviation peaked at 139; quiet frames were mostly 3-8. No frame reached 300. These are codec-decoded measurements, not an independent analog calibration or a specification for every board.

Firmware 0.3.1 lowers the threshold to 40. Speech still needs three consecutive qualifying 20 ms frames. The existing behavior remains: discard after five seconds without detected speech, finish after about 1.2 seconds of silence after speech, and cap a recording at 60 seconds. A single B press while recording saves immediately.

The threshold is now in firmware/src/codec.h alongside the gate interface, so the native tests compile and exercise the actual production default. Two regressions cover quiet speech with short pauses and background noise with a brief click. Synthetic samples are used; captured audio is not committed.

## Bluetooth status

The phone's server test verifies its connection to the receiver and token only. It does not check Bluetooth.

The recorder advertises while recordings await delivery or the physical pairing window is open. Once its queue empties, it disconnects and stops advertising. Therefore Waiting for recorder can be normal between recordings. Tap Refresh status to update the displayed diagnostics.

A saved recording that reaches the phone confirms the Bluetooth data path. A corresponding receiver record and transcript confirm the later upload and transcription steps. The manual test verified that complete path.

## Verification

- The quiet-speech regression fails with the former threshold of 300.
- All eight native codec/gate tests pass with 40, including DC-offset rejection, no-speech cancellation, pause handling, and the 60-second cap.
- The 21 build-helper, firmware preflight, and NVS-geometry tests pass.
- The personal 0.3.1 clean build passed generated DTS/Kconfig, HEX/ELF, and checksum checks.
- OpenOCD programmed and readback-verified all 197,112 image bytes on the connected board. Debug recovery was not used.
- Hardware breakpoints confirmed successful Bluetooth initialization and application startup; the breakpoints were removed and execution resumed.
- The next physical automatic-stop test is pending.

The private delivery is in firmware-v0.3.1 beside the source folder, with its build manifest and verification records. The HEX SHA-256 is ede6418fa3a9cdb18713685096fd9d9a0c479263f0f698320a27c205ab098da5. Keep the firmware and pairing card private.

## Next physical check

1. Keep the board on USB and the phone relay and Tailscale running.
2. Double-click B and speak continuously for about eight seconds near the microphone.
3. Pause for roughly two seconds. The green light should go out and the recording should transfer.
4. Refresh app status and verify the new server transcript.
5. Repeat at the intended wearing distance, with natural pauses and ordinary room noise.

This first threshold is based on one board and a short recording. Distance, noise tolerance, automatic-stop timing, and speech accuracy still need physical testing. Electrical charger/battery measurements and power-interruption recovery also remain open.
