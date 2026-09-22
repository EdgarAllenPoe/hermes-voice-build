# 05 — Interface specification: HVB1, BLE and HTTP

This is the version-1 contract shared by all supplied components. Any incompatible change requires a protocol/version change and coordinated updates. Samples and integer fields are little-endian unless stated otherwise. UUIDs use canonical network byte order, not the mixed-endian layout of a Windows GUID structure.

## HVB1 file header

| Offset | Bytes | Meaning |
|---:|---:|---|
| 0 | 4 | ASCII `HVB1`; written last by recorder to commit |
| 4 | 1 | Codec = 1 (independent IMA-ADPCM frames) |
| 5 | 1 | Channels = 1 |
| 6 | 2 | Header bytes = 64 |
| 8 | 4 | Sample rate = 16000 |
| 12 | 4 | Sample count, positive multiple of 320, maximum 960000 |
| 16 | 4 | Payload bytes = sample_count / 320 × 164 |
| 20 | 4 | IEEE CRC-32 of payload, compatible with Python `zlib.crc32` |
| 24 | 16 | Nonzero UUID; recorder generates a random v4 ID |
| 40 | 4 | Flags: bit 0 max-duration stop; bit 1 manual stop; others zero |
| 44 | 4 | Device uptime milliseconds at allocation; wraps, **not wall-clock time** |
| 48 | 8 | Recorder sequence number recovered from committed slots |
| 56 | 8 | Reserved, zero |

A file is exactly 64 + payload bytes, with a maximum of 492,064 bytes. Reject unknown formats rather than guessing. A complete valid file is immutable once committed. SHA-256 is computed by receivers for duplicate/collision handling; it is not stored in the MCU header.

## Independent ADPCM frame

Each 164-byte frame represents exactly 320 PCM samples. Bytes 0–1 are the initial signed predictor; byte 2 is the initial step index (encoder uses zero; valid range 0–88); byte 3 is reserved zero. Bytes 4–163 contain 319 four-bit IMA codes, **low nibble first**. The unused high nibble of the last byte is zero. Decoder and encoder use the standard 89-entry IMA step table embedded in `codec.c` and `audio.py`.

There is no cross-frame predictor dependence. It is not directly a WAV `IMA_ADPCM` stream. Use `tools/audio_tool.py decode` or the receiver's decoder to produce 16 kHz mono PCM WAV.

One second uses 50 × 164 = 8,200 payload bytes. Fifteen 512 KiB slots reserve space for fifteen recordings regardless of their duration. Erase metadata lives at each slot's last four bytes outside the transmitted file.

## BLE service

Service UUID: `58ef0001-35c8-4c31-89aa-81f763051da1`.

| Characteristic first group | Access | Meaning |
|---|---|---|
| `58ef0002` | Authenticated write | Control commands |
| `58ef0003` | Authenticated read | Selected message metadata |
| `58ef0004` | Authenticated read | Selected audio chunk |
| `58ef0005` | Authenticated read | Basic device diagnostic status |

The remaining UUID groups match the service UUID. Access requires a bonded authenticated encrypted link. Do not reinterpret these as a Bluetooth headset or standard Bluetooth LE Audio service.

### Control commands

**NEXT:** write byte `01`. Recorder selects the oldest committed slot. Read META: 16 UUID bytes followed by a little-endian uint32 total file length. Twenty zero bytes means no message. Selection applies to the current connection.

**READ:** write byte `02` followed by little-endian uint32 file offset. Read DATA to receive up to 180 bytes from that offset, including the header when offset is zero. Offset must be less than total length. Repeat until the advertised length is received. Android requests ATT MTU 247; long reads can handle a smaller MTU. There is only one outstanding operation at a time.

**ACK:** after full validation and durable local commit, write byte `03` followed by the exact 16 UUID bytes. This marks the slot eligible for reclamation. ACKing only a RAM buffer or before checksum verification is a protocol violation.

Legacy INFO is eight bytes: protocol byte 1; capture/tentative-mic-active byte; committed-message count byte; reserved byte; uint16 battery millivolts (zero when not available); uint16 reserved. Voltage is diagnostic, not calibrated percentage.

On disconnect the selection is lost. The phone requests NEXT again and may resume its `.part` file when the ID/length still match. Invalid complete partial data is discarded on the phone and downloaded again; it is not ACKed.

## Flash transaction boundaries

The recorder writes an allocated marker, then payload frames, then header bytes 4–63, and finally the four-byte magic. The ACK changes the slot marker, after which background erasure progresses by sectors, with the trailer sector erased last. The flash driver's write/erase behavior, alignment and power-interruption properties still need real-device testing. Host simulation covers logic, not analog brownout behavior.

If power disappears during recording or commit, there may be no completed message to recover. If the phone already committed a recording but its ACK response was lost, re-transfer is safe: matching ID+hash is accepted as a duplicate before another ACK.

## HTTP API

`POST /v1/voice`

```http
Authorization: Bearer LOCAL_SECRET_TOKEN
Content-Type: application/octet-stream
Content-Length: EXACT_HVB1_FILE_BYTES
```

The body is the complete HVB1 file. Chunked encoding, oversized bodies, unknown formats, invalid checksums and conflicting IDs are rejected. The token is unrelated to the BLE passkey. Never include it in the URL.

After SQLite commit:

```json
{"id":"RECORDING_UUID","sha256":"HEX_SHA256","accepted":true,"duplicate":false}
```

The response status is `202`. Android verifies `id`, `sha256` and `accepted` before clearing its queued audio. The receiver also returns 202 for an identical duplicate, with `duplicate: true`.

| Status | Interpretation |
|---:|---|
| 202 | Durably accepted; not necessarily transcribed or processed |
| 400 | Malformed/truncated/corrupt format |
| 401 | Wrong or missing bearer token |
| 404 | Wrong path |
| 409 | Same ID with different bytes; investigate, do not overwrite |
| 411 | A single content length is required |
| 413 | Too large/empty |
| 415 | Wrong media type |
| 507 | Queue quota reached; sender retains recording |
| 500 | Not acknowledged; retain and retry later |

`GET /health` uses the same Authorization header and returns protocol/status only. No web administration, transcript-fetch or remote-delete API is exposed.

## Queue state and exactly-once boundary

Server flow is `queued → transcribing → review/ready → delivering → done`. Transcription failures become `failed`; interrupted or failed agent handoffs become `uncertain`. On worker restart, interrupted transcription is requeued, but interrupted dispatch is not automatically replayed.

A UUID deduplicates file delivery. It cannot prove an external agent's action occurred exactly once. There is no transaction spanning SQLite and all of Hermes's possible tools. Operator review of uncertain dispatch is therefore part of the contract.

## Compatible diagnostics extension in firmware 0.3

The first eight INFO bytes retain their HVB1 meanings. Firmware 0.3 returns 32 bytes.
Clients must accept the legacy eight-byte response and must not infer optional capabilities from a version string alone.

| Offset | Size | Meaning |
|---|---|---|
| 8 | 1 | Diagnostic schema 1 |
| 9 | 3 | Firmware major, minor and patch |
| 12 | 4 | Microphone startup failures |
| 16 | 4 | Audio read/size failures |
| 20 | 4 | Dropped button edge events |
| 24 | 4 | Flash read/write/erase errors |
| 28 | 2 | Quarantined or broken slots |
| 30 | 2 | Capabilities; bit 0 supports SKIP |

Counters are unsigned little-endian and reset at boot. Battery voltage remains a diagnostic reading, not a calibrated percentage. Android's display is a timestamped snapshot; version 0.5 also refreshes it while connected.

**SKIP:** only with capability bit 0, write byte 04 followed by the selected UUID.
This excludes that slot from later NEXT selections on the same connection. No flash marker is written and no audio is deleted. The exclusion resets on disconnect. Android attempts three complete invalid downloads before using SKIP and preserves the rejected bytes in its private incoming directory with suffix .bad.

At boot, firmware validates committed header fields, frame structure and payload CRC. Corrupt committed slots become quarantined and are not returned by NEXT. Bytes remain intact, capacity is reduced, and diagnostics report the quarantined count. There is no automatic destructive recovery command. A corrupt message must never be acknowledged merely to clear the queue.


## Compatible controls extension in firmware 0.4.0

INFO is now 64 bytes. Offsets 0–31 retain the previous meanings. Capabilities at offset 30 are: bit 0 SKIP, bit 1 inventory and guarded delete, bit 2 capture settings, bit 3 microphone test, bit 4 charging diagnostics. Current firmware advertises 31. Legacy clients may keep using the original fields and commands.

| Offset | Size | Meaning |
|---|---|---|
| 32 | 1 | Extension schema = 2 |
| 33 | 1 | Charge state: 0 unknown, 1 battery, 2 charging, 3 USB not charging, 4 problem |
| 34 | 1 | Low-battery flag |
| 35 | 1 | Mic test: 0 off, 1 starting, 2 active, 3 error |
| 36 | 4 | Signed charging current in mA |
| 40 | 2 | DC-independent audio level |
| 42 | 2 | Peak level during this test |
| 44 | 2 | Silence delay in milliseconds |
| 46 | 2 | Audio-level threshold |
| 48 | 1 | Manual-save mode: 0 or 1 |
| 49 | 1 | Free recorder slots; erasing slots are not yet free |
| 50 | 2 | Reserved |
| 52 | 4 | Mic-test frames processed |
| 56 | 8 | Reserved |

**Inventory:** characteristic `58ef0006-35c8-4c31-89aa-81f763051da1`, authenticated read. Header `[1, count, 0, 0]` is followed by up to 15 rows, each 30 bytes: slot (1), damaged flag (1), UUID (16), file length (4), duration milliseconds (4), sequence low 32 bits (4). A read starting at offset zero captures a snapshot retained through subsequent long-read offsets. Slot numbers are zero-based. Damaged rows can have unknown length/duration/ID. Inventory contains no audio.

**Guarded delete:** capability bit 1; write control `[05, slot, UUID16]` (18 bytes). Only a committed or quarantined slot with that exact ID can be deleted. A stale slot/ID, writing slot, or active capture is rejected. The durable discard marker precedes normal background erasure. This is distinct from ACK, which still requires a validated durable phone copy. Android serializes these operations with normal transfer, requires a recent connected inventory, and confirms the specific rows before sending.

**Capture settings:** characteristic `58ef0007-35c8-4c31-89aa-81f763051da1`, authenticated read/write. Exactly six bytes: silence milliseconds (u16; 2000/4000/6000), threshold (u16; 10–1000), manual flag (u8; 0/1), reserved zero. Defaults are 2000/40/0. Validate all fields before writing persistent Zephyr settings; a successful write updates the live configuration. Active capture rejects changes and uses a snapshot of its starting settings. Manual mode disables silence/no-speech stopping but retains the 60-second cap.

**Microphone test:** capability bit 3; control `[08, 01]` starts, `[08, 00]` stops. Starting during capture is rejected. The test allocates no recording slot and sends only levels/counters in INFO. It stops on disconnect, button press, cancellation, or its 60-second deadline. Android requests stop when leaving the controls screen.

## Authenticated processing status

`GET /v1/messages/<canonical-lowercase-UUID>` uses the existing bearer token. Success (200) returns only `id`, `sha256`, `state`, `created`, and `updated`. Unknown IDs return 404; malformed IDs return 400. No audio, transcript, or Hermes result is returned. `/health` advertises `message_status: 1`.

The Android client checks the ID and hash against its verified upload receipt before accepting a stage update. Failed status requests do not remove receipts or cause another upload. A completed worker handoff (`done`) is separate from proof of every downstream tool action. No remote deletion API is introduced.
