# Prompt to give Hermes

Copy the text below into Hermes.

---

Please optimize my existing Hermes Voice intake workflow for a faster response while preserving its established behavior and delivery guarantees. Inspect the current implementation and make the changes, then explain what you changed and measured.

## Current installation and changes already made

- The receiver runs under my `tom` account. Its configuration is `~/.config/hermes-voice/config.json`; the Python package is `~/.local/share/hermes-voice/receiver/hvbridge`.
- The bridge uses `~/.local/share/hermes-voice/voice_launcher.py`. The launcher invokes `/usr/local/bin/hermes --profile default chat --skills hermes-voice-intake --oneshot --quiet --source voice-recorder --query-file PATH`.
- Each recording has a UUID and a work directory at `~/.local/state/hermes-voice/work/UUID/` containing `prompt.txt`, `outcome.json`, and delivery records.
- The recorder and Android app now use bounded Bluetooth notification batches and immediate recording-ready events. The physical controls are one press to start and another press to save immediately.
- Transcription now uses local Whisper `base.en`, explicit English, and six CPU threads. Five recent recordings matched the previous model's normalized words while taking roughly 1.4–1.7 seconds each in isolated tests. One earlier baseline recording took 10.6 seconds. This is a limited comparison, not a universal accuracy guarantee.
- The worker now has one transcription lane and one serial Hermes-delivery lane. Transcription can prepare the next recording while Hermes handles the previous one. Local post-commit socket events wake the worker. The database remains the authoritative durable queue.
- Stage timings are available through the authenticated `/v1/messages/UUID` response in optional `timings_ms` fields: `queue_wait`, `transcribe`, `setup`, `hermes`, and `total`. The `hermes` measurement includes launcher startup, agent work, and Telegram delivery. It does not isolate model latency.
- The latest real button test completed successfully in Telegram. Its server time was 51.1 seconds: queue wait 0.30 seconds, transcription 5.10 seconds (including 3.57 seconds loading the model), first launcher compatibility/setup 2.94 seconds, and Hermes/Telegram 41.99 seconds. Bluetooth separately took 1.65 seconds for 23,680 bytes. An earlier different message took 39.1 seconds overall with 25.45 seconds in Hermes. These are different inputs and do not prove an overall speedup. Please profile the Hermes stage instead of assuming every delay comes from the model.

## Requested Hermes improvements

1. Read the existing `hermes-voice-intake` skill and identify my established note destinations and naming conventions. Keep those destinations. Do not invent another notes system.
2. Add an efficient path for a clear, simple note or idea. Use only the context and tools needed to capture it in the established destination, verify that capture, and produce the existing outcome file. Avoid unnecessary planning, unrelated research, broad filesystem searches, and loading unrelated conversation history for these standalone notes.
3. Keep the full workflow for complex requests. A request that needs research, multiple steps, clarification, or normal approval must retain those capabilities. If the speech or destination is ambiguous, produce a concise clarification through the established outcome/delivery mechanism.
4. Measure startup, model calls, tool execution, outcome writing, and Telegram sending separately. Check for redundant skill/context loading or repeated capability probes. The receiver now caches its CLI compatibility check for the lifetime of an unchanged executable.
5. Consider a faster model or lower reasoning effort for simple capture only if the installed Hermes version supports an explicit, reliable configuration. Preserve a suitable model for difficult tasks. Report the chosen configuration and any accuracy tradeoff; do not silently replace my general default model or change unrelated workflows.
6. Keep voice actions serial and in order. The receiver already overlaps transcription with agent work. Do not create parallel agent actions that could reorder notes or cause conflicting external operations.

## Required existing contracts

- Treat each voice UUID as its own standalone input. Do not continue the most recent terminal conversation by accident.
- Preserve `outcome.json`: matching `uuid`; a status from `Captured`, `Completed`, `Needs clarification`, `Awaiting approval`, or `Failed`; string fields `requested` and `completed`; and string-list fields `locations`, `pending`, and `failures`.
- Write outcomes atomically and describe only actions that actually succeeded. A plan is not a completed action.
- Preserve the launcher's UUID validation, outcome reuse, and Telegram delivery markers. A confirmed delivery must not be sent again. A started/uncertain delivery requires review before any retry.
- The launcher owns Telegram delivery after validating the outcome. The intake skill must not also send a duplicate Telegram reply. Keep the configured destination and existing concise response style.
- Preserve normal confirmation rules and uncertainty checks. Do not expose tokens, secrets, or private message contents in timing reports.
- Do not clear the receiver queue, delete recordings, retry historical failures, or replay completed UUIDs as part of this optimization.

Use synthetic/mocked cases first: a simple note, a complex request, ambiguous transcription, an existing confirmed outcome, and an uncertain Telegram delivery. Then ask me for one normal voice test. Compare before/after timings with the same task type. Give me the exact files/settings changed, test results, and remaining bottlenecks. Keep the current working configuration available for rollback.
