# Change log

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
