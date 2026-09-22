# Change log

## Android 0.6.0 / firmware 0.5.0 — September 22, 2026

Added bounded BLE notification batches with token/offset validation, safe pull fallback and recording-ready events. Added local post-commit worker wakeups, concurrent transcription with serial ordered Hermes delivery, cached launcher checks, concise intake instructions, and content-free stage timing metadata. Selected English base.en with six CPU threads after a five-recording comparison. Preserved second-press immediate save, the configured automatic-save option, durable acknowledgements and existing pairing identity. Added a complete prompt for Hermes-specific speed work.

## Android 0.5.0 / firmware 0.4.0 — September 22, 2026

Implemented recording history with authenticated server processing stages, guarded recorder/phone queue controls, automatic and manual reconnection, persistent silence/sensitivity/manual-save settings, measured battery/charging status and low-battery alert, save/transfer/error LED patterns, local playback with optional 24-hour delivered-audio retention, and a bounded microphone-level test that never saves or uploads audio. Added a backward-compatible 64-byte BLE INFO extension, inventory/configuration characteristics, and metadata-only server status endpoint. Database schema 3 preserves existing audio and receipts. Tests cover migrations, deletion guards, retention, codec playback, capture policy, and server authentication. See the current user guide and release verification for physical test results and remaining acceptance work.

## v0.1 — September 12, 2026

Initial complete custom-source package: C recorder firmware, native Java Android relay, Python bridge and worker, private protocol, durable queues, provisioning/build helpers, 56 host tests, documentation and printable copies.

Corrections to the earlier discussion:

- System-ON idle is the initial firmware baseline. System-OFF wake/reconnect and weeks-to-months battery runtime are not promised or measured.
- Locked-phone operation is a design target requiring actual phone testing, not a universal guarantee. First unlock after reboot and recovery after force-stop remain relevant.
- BLE association and secure Bluetooth bonding are separate. Physical pairing uses a locally generated private passkey.
- A disconnected mating battery lead is recommended for assembly/service even when the builder is proficient at soldering. Retain the cell's factory protection.
- The referenced board schematic uses a fixed resistor on the charger's thermistor input. It does not measure the selected battery's temperature.
- No assumption is made about the user's Hermes operating system, executable, CLI syntax, phone model or Tailscale address.
- Hermes jobs are separate CLI queries unless a different adapter is deliberately implemented. Server review mode is the default.
- UUIDs suppress duplicate capture uploads. Exactly-once arbitrary agent side effects are not guaranteed; uncertain delivery requires review.
- No APK, firmware binary, external SDK/toolchain/model, or fit-validated enclosure STL is represented as included or tested.

## 0.3.0 prehardware reliability

Added a host-tested Android transfer controller with worker-thread I/O, queue schema 2 migration, per-message HTTP holds and backoff, diagnostic export and server health testing. Added firmware boot CRC quarantine, non-destructive connection-local SKIP, capture/flash counters and testable button timing. Added stable personal identity restoration, isolated CI app builds, receiver transcript editing/rejection, fair scheduling and explicit cleanup. Added real speech comparison tooling, emulator database tests and the printable hardware workshop guide. Hardware acceptance remains outstanding.

## Firmware 0.3.2 single press recording

A short press and release starts recording. Microphone warmup begins on press; stable release confirms the capture and green LED. Pairing still uses a 1.5-second idle hold, and bond reset a 10-second idle hold. Holding a manual-stop press cannot accidentally pair or forget. Release debounce tolerates mechanical switch bounce, including events queued during microphone warmup. Speech now ends after two seconds of silence instead of 1.2 seconds. Speech sensitivity, five-second no-speech cancellation, 60-second limit, BLE protocol, pairing identity and storage layout are unchanged.

## Firmware 0.3.3 charging indicator

Enabled the nPM1300 LED driver and the separate red LEDDRV1 charging light. A low-priority task samples USB status, cell voltage and charging current about once per second, including during recording. Following Seeed's XIAO example, the indication uses current rather than the D00 COMPLETE flag. Read errors, charger faults, thermal pause and invalid USB conditions turn the indication off. Charging limits remain 100 mA / 4.20 V. Eight native C policy tests cover measured charging, taper, completion, absent power/cell, faults and current thresholds. Updated the printable workshop guide.
