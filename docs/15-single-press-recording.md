# Single press recording in firmware 0.3.2

Tap either the external button or onboard B button once and release it. Wait for the green light, then speak. Pause for about two seconds to save, or press once while recording to save immediately.

| Gesture | Result |
| --- | --- |
| Short press and release while idle | Green light; recording starts |
| About 2 seconds of silence after speech | Recording saves and green goes out |
| Press once while recording | Immediate manual save |
| No speech detected for 5 seconds | Capture is discarded |
| Record for 60 seconds | Recording saves at the limit |
| Hold 1.5 seconds while idle, then release | Blue pairing indication; pairing window opens |
| Hold 10 seconds while idle | Forget the old phone bond; recordings remain |

Hold gestures do not create recordings. Holding the press used to stop recording cannot enter pairing or reset the bond. After green goes out, a new tap starts another recording.

The two-second timer restarts when the microphone detects speech. A 1.5-second pause should remain inside the same recording. This uses the same simple energy gate and threshold as 0.3.1; room noise and speaking distance still matter.

## Compatibility

The recorder keeps the existing Bluetooth identity, passkey and storage format. The update does not require a phone app update or new pairing. Android 0.4.0 may still show a double-press tip; use the single-press controls above after installing this firmware. Future app builds use a gesture-neutral tip.

Battery and charging configuration are unchanged. The unresolved battery investigation is separate from this controls update.

## Implementation and automated verification

Microphone warmup begins at button-down. A release that remains stable for 60 ms starts the stored capture, retaining up to 240 ms of available pre-roll. Pending switch bounce is drained after microphone warmup before confirming release. The green LED appears when the capture has been allocated successfully.

The production C controller is tested with 21 deterministic button traces, including bounce, duplicate edges, delayed polling, pairing/forget boundaries and holding manual stop. Ten native codec/gate tests include the exact two-second threshold, a resumed phrase after a 1.5-second pause, the no-speech timeout and the 60-second cap.

## Physical acceptance

After programming, confirm:
1. One tap and release lights green; speak a harmless short test phrase.
2. Pause for about 1.5 seconds, then continue. Green should remain on.
3. Stop speaking for about two seconds. Green should go out and the message should reach the phone/server.
4. Repeat and press once while speaking. It should save immediately.
5. When idle, hold for pairing and release. No recording should be saved.

Compilation and flash readback do not prove microphone behavior or end-to-end delivery. Record the results below after the actual device test.

## Build and programming record

On September 19, 2026, the personal clean firmware build passed all generated DTS/Kconfig, HEX/ELF, package and checksum checks. All 52 relevant local tests passed. The image was programmed on the retained XIAO nRF54LM20A Sense and all 197,140 image bytes passed readback verification. The recorder was idle with all 15 slots free beforehand; its microphone/audio/button-edge error counters were zero. Automatic mass-erase recovery was disabled.

The private bundle is firmware-v0.3.2 beside the source folder. Its pairing card matches the previous 0.3.1 delivery. HEX SHA-256: eaafd8fa989bac44deba8c02e1c0189b9dddcbc9ed0c7b50934db2b12bb8ec96. Do not publish the firmware or private pairing card.

The user-operated tap, pause, and delivery acceptance test is pending. The build and programming checks alone do not establish those behaviors on the physical board.
