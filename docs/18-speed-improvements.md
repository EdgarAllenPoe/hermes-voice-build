# Speed improvements: app 0.6.0 / firmware 0.5.0

## Button use

Press and release once to start. Speak when the light turns green. **Press and release once again when finished to save immediately.** This behavior already existed and remains unchanged. Automatic saving after the configured silence interval is still available. Select **Save only when I press again** in Recorder controls if you want to disable automatic silence saving; the 60-second cap remains.

## Faster path through the system

1. **English transcription:** the deployed server uses `ggml-base.en.bin`, language `en`, and six CPU threads. The old multilingual `small` model is retained for rollback. A local comparison on five recent recordings produced the same normalized words in 1.4–1.7 seconds with the new configuration. The earlier latest sample took 10.6 seconds with automatic language detection and the small model. This measures transcription only, not the whole response, and is not a general accuracy test. If other languages are needed, restore a multilingual configuration deliberately.
2. **Less Hermes overhead:** the bridge caches a successful launcher compatibility check until the executable changes, and its intake prompt asks for efficient handling of straightforward notes. [The full prompt for Hermes](HERMES-SPEED-OPTIMIZATION-PROMPT.md) covers internal skill/model/tool optimization. That further Hermes-specific work remains for Hermes to perform after receiving the prompt.
3. **Batched Bluetooth:** the phone subscribes to a new authenticated notification characteristic. One request yields up to 16 packets, each carrying up to 235 audio bytes at the current MTU of 247, with absolute offsets and a per-request token. Legacy 180-byte pull transfers remain available. Interrupted bursts retain the partial file and can fall back to pull; Reconnect allows another burst attempt.
4. **Immediate wakeups:** saving a recording triggers a notification to the phone. New server queue work emits a local post-commit socket event. Fallback checks remain for lost wakeups: five seconds on a notification-capable phone connection, and 30 seconds for the server's durable queue. Older firmware retains the two-second phone poll.
5. **Overlapping work:** one transcription worker prepares the next message while a separate worker completes the previous Hermes action. Only one Hermes delivery runs at a time, with earlier queued/transcribing messages considered before later ready ones. Held/review/failed messages do not indefinitely block newer work.

## Reliability and timing

CRC and format checks, durable phone commits before recorder acknowledgement, authenticated connections, receipt hashes, UUID deduplication, and uncertain-handoff review remain. No audio is streamed to Hermes before a complete recording is validated. There is no new automatic replay of failed agent actions.

Diagnostics include transfer mode, milliseconds for the last transfer session, and available timing metadata for a checked server recording. A transfer resumed after disconnection has a new session duration. Server timing fields are milliseconds; `total` starts at server receipt and ends after the launcher finishes. `hermes` includes agent and Telegram work, not just model computation. Phone-to-server transit and the user's recording time are separate.

The receiver adds a timing table with an additive schema-3 migration. Existing audio and receipts are preserved. Wakeups are advisory; a stopped worker catches up from SQLite when restarted. The socket is private to the server account and is not a network API. Processing is still subject to radio conditions, Android background restrictions, available CPU, model latency, and the work requested.

See [the release verification record](../SPEED_0.6.0_VERIFIED.md) for measured hardware results and remaining acceptance checks. The OpenVINO backend was not required for the selected configuration: the existing build and smaller model already achieved the measured improvement without adding a second inference runtime.

## Confirmed real recording

Tom confirmed that the second press saved the recording immediately and that Telegram received it. The phone transferred 23,680 bytes in 1.645 seconds using notifications; the recorder then showed zero queued recordings and all 15 slots free. There is no comparable previous Bluetooth timing, so this is a measured result rather than a claimed speedup ratio.

Server completion took 51.1 seconds: 0.30 seconds queue wait, 5.10 seconds transcription, 2.94 seconds first launcher setup, and 41.99 seconds Hermes/Telegram. The transcription log attributes 3.57 seconds to model loading. The isolated 1.4–1.7-second transcription comparisons therefore do not predict every live request. The earlier different recording completed in 39.1 seconds, so a whole-system speedup has not yet been demonstrated. Hermes processing remains the largest measured delay; pass it the linked prompt for that next optimization.
