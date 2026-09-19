# Prehardware improvements in version 0.3

Version 0.3 retains the HVB1 recording format. Hardware remains untested. See the [current test report](../TEST_REPORT_v0.3.md) for executed results and the [workshop guide](HARDWARE-GUIDE.md) for wiring, programming and test worksheets.

## What changed

- The production Android transfer controller runs on a dedicated worker thread. A host simulator exercises interrupted transfers, durable-commit failure, duplicate receipts, lost ACKs, corrupt recordings, all 15 slots, and the 60-second maximum.
- Android queue schema 2 migrates schema 1 without discarding audio. HTTP 400, 409, 413 and 415 hold the individual message; subsequent messages can upload. HTTP 401, 403, 404 and 411 require configuration attention. Other failures back off from 30 seconds to one hour. Periodic scheduling remains subject to Android's background restrictions.
- Firmware validates committed headers, frame structure and CRC on boot. Corrupt slots are quarantined and preserved. They consume capacity until a deliberate recovery process is designed; do not mass erase a board containing wanted audio.
- An optional connection-local SKIP command defers one recording without ACKing or deleting it. Android uses it only when firmware advertises support and the same recording failed validation three times. Legacy firmware retains its existing retry behavior.
- The actual button controller is tested independently for debounce, single-tap release, long holds and manual stops (firmware 0.3.2). Empty manual captures release their allocated slot.
- Phone diagnostics show connection, transfer progress, last upload and the latest recorder status snapshot. Export contains no token, endpoint, Bluetooth address, transcript or audio. Counters reset when the recorder reboots; status is refreshed when it connects.
- Personal Android builds require the preserved signing key. CI builds use package org.tomstout.hermesvoice.ci and a disposable key. They cannot update the personal package. CI firmware is separately provisioned test output and is not the owner's stable recorder identity.
- Receiver CLI supports inspecting, correcting, approving and rejecting transcripts. Work scheduling selects the oldest eligible transition to avoid starvation.
- Explicit cleanup can remove completed/rejected working files and database content while retaining UUID/hash receipts. Preview is the default. Applying requires the worker to be stopped.
- A local speech evaluator compares original PCM with HVB1-compressed audio using the configured Whisper executable and model. It never invokes Hermes.

## Tests and their limits

Run bash tools/run_tests.sh on Linux with Python, GCC, Git and JDK 17+. The suite exercises real codec, storage and button C code through host shims; actual electrical timing is not simulated.

The Android database tests run against real SQLite on an API 35 emulator in an isolated CI app. They verify schema 1 migration, durable reopening, held-message progress, retry eligibility, receipt deduplication, and FULL synchronization with WAL. Android OpenParams configures this durability setting for the connection pool. This does not establish Bluetooth operation or Android 17 locked-phone behavior.

The speech workflow uses upstream whisper.cpp v1.7.6, its public JFK sample and tiny.en. The report includes source, executable and model hashes and original/compressed word errors. This is a real transcription smoke test, not evidence of accuracy for your voice, room, phone, recorder microphone or the production small model. Keep private speech and reports in ignored speech-eval/.

## Personal build identity

The private backup from the original delivery contains the matching Android key and recorder pairing pair. Restore it without rotating anything:

    py -3.11 tools/restore_identity.py --backup "C:\Users\tomst\Desktop\Hermes-Voice-PRIVATE-Keys-and-Pairing.zip"
    py -3.11 tools/restore_identity.py --backup "C:\Users\tomst\Desktop\Hermes-Voice-PRIVATE-Keys-and-Pairing.zip" --apply

The first command previews. The helper verifies backup hashes, preserves matching files, refuses different existing identities, and restores only the app key, recorder header and card. It does not extract the recovery private key. The restored destinations are ignored by Git.

Personal builds automatically use config/private/android-debug.keystore unless HVB_DEBUG_KEYSTORE points to another existing key. Preserve that key and the matching pairing files. Do not set HVB_CI_BUILD for the app you intend to update in place.

## Speech evaluation

Copy fixtures/speech-manifest.example.json into an ignored speech-eval/ directory. Record each phrase to a separate mono 16 kHz 16-bit PCM WAV, at most 60 seconds. Put the exact words actually spoken in each reference field; use an empty reference for a no-speech case. Avoid consequential commands.

    python tools/evaluate_speech.py speech-eval/manifest.json --out speech-eval/run-001 --whisper /absolute/path/whisper-cli --model /absolute/path/ggml-small.bin

Omit --whisper and --model to prepare original/compressed comparison files without transcription. Each output directory must be new. Listen to both WAVs, compare the transcripts and inspect report.json. Word error rate ignores case and punctuation; it does not measure whether a changed name/date is acceptable. Noise hallucinations appear as unexpected_words. Never approve a hallucinated command.

## Receiver review and cleanup

All commands use the existing private config and run on the Linux Hermes host. Replace MESSAGE_UUID with a specific ID returned by status:

    python3 -m hvbridge --config ~/.config/hermes-voice/config.json show MESSAGE_UUID
    python3 -m hvbridge --config ~/.config/hermes-voice/config.json edit MESSAGE_UUID --transcript-file /absolute/path/corrected.txt
    python3 -m hvbridge --config ~/.config/hermes-voice/config.json approve MESSAGE_UUID
    python3 -m hvbridge --config ~/.config/hermes-voice/config.json reject MESSAGE_UUID

Choose approve OR reject. Only review-state transcripts can be edited or rejected; the first unedited transcript is preserved until deliberate cleanup. Approval makes that message eligible for Hermes execution. Rejection preserves its receipt and audio until cleanup.

    python3 -m hvbridge --config ~/.config/hermes-voice/config.json storage
    python3 -m hvbridge --config ~/.config/hermes-voice/config.json cleanup --older-than-days 7
    systemctl --user stop hermes-voice-worker.service
    python3 -m hvbridge --config ~/.config/hermes-voice/config.json cleanup --older-than-days 7 --apply
    systemctl --user start hermes-voice-worker.service

The preview lists exactly the eligible IDs and known files. Applying deletes only audio.wav, transcript.txt, prompt.txt, whisper.log and hermes.log for old done/rejected messages and clears their database content. Other files, pending work and receipts remain. This is not secure erasure of SQLite pages, backups or Hermes history. Back up deliberately before cleanup.


## Rebuild the printable guide

The DOCX and Markdown guide are generated from docs/hardware-guide.json by tools/build_hardware_guide.py using python-docx. The wiring PNG comes from the adjacent original SVG. Regenerate the DOCX after changing the text or diagram, then check every rendered page before claiming print-layout verification. The authoring environment used python-docx 1.2.0; this is a document-generation dependency, not an application/runtime dependency.

The guide uses US Letter paper with 0.8-inch side margins, numbered workshop stages, wiring labels and spaces for measurement/results. Keep generated page previews outside the public repository; only the guide and its public source assets belong in Git.
