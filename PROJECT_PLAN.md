> **Historical record:** The original text below describes an earlier authoring session. Both targets subsequently compiled in run `34752997335`; see [README.md](README.md) and [VERIFIED_BUILD.md](VERIFIED_BUILD.md) for current status. Physical hardware acceptance remains outstanding.

# Hermes Voice Button — Complete Project Plan

**Version 0.1 • September 12, 2026 • Prepared for Tom**

## 1. Purpose and user experience

Build a compact rechargeable device that captures an idea with minimal interaction:

**Double-click → speak → pause → put the device away.**

The recorder starts locally; it does not wait for Bluetooth, Android, Tailscale, transcription, or Hermes. The phone is a relay and may remain locked with its screen off after initial setup and the first unlock following a reboot. Gemini or another chosen assistant remains the phone's assistant.

This is an engineering prototype, not a verified finished device. The supplied host tests pass, but no APK/MCU target build or physical test has been completed in the authoring environment. Actual microphone start latency and background-phone behavior must be measured before relying on it.

## 2. System architecture

```text
Momentary button + microphone
        |
XIAO: capture → compress → durable flash queue
        |
Authenticated Bluetooth LE GATT
        |
Android: validate → durable private queue → acknowledge XIAO
        |
Tailscale: HTTPS preferred, private HTTP optional
        |
Linux receiver: validate → SQLite commit → HTTP 202
        |
Worker: local speech-to-text → review/automatic dispatch → Hermes
```

Each stage acknowledges durable receipt rather than successful AI processing. A network outage delays delivery but should not discard a completed recording. An unexpected loss of power during the active recording can still lose that unfinished message; the design does not promise otherwise.

## 3. Hardware choice

The recommended controller remains the **Seeed Studio XIAO nRF54LM20A Sense**, the Sense version specifically. Relevant board features are its nRF54LM20A processor, PDM microphone, 8 MB external flash, USB-C, user button, RGB LED and nPM1300 charging/power management. The board footprint is 21 × 17.8 mm, and its package includes an external 2.4 GHz FPC antenna. [S1, S2, S3]

We use a 350 mAh protected, single-cell LiPo and one external momentary button. No speaker, display, separate microphone, charging board or phone microphone is required. The onboard button is usable during initial tests; the external switch gives better placement and tactile feel.

The board support package is important: firmware here targets Seeed's PlatformIO/Zephyr distribution, not an assumed interchangeable upstream Zephyr board definition. Build-time device-tree checks are mandatory.

## 4. Shopping list and sources

Prices are US-dollar listed prices checked September 12, 2026; tax, shipping and availability at checkout can differ. Select the **Sense / unsoldered / one-board** option when ordering.

| Item | Qty to buy | Listed price | Source / part |
|---|---:|---:|---|
| XIAO nRF54LM20A Sense | 1 | $15.90 | [Seeed, SKU 100018440](https://www.seeedstudio.com/Seeed-Studio-XIAO-nRF54LM20A-Sense-p-6840.html) |
| Protected 3.7 V 350 mAh LiPo, short cable | 1 | $5.95 | [Adafruit 4237](https://www.adafruit.com/product/4237) |
| 6 mm tactile switches, pack of 20 | 1 pack | $2.50 | [Adafruit 367](https://www.adafruit.com/product/367) |
| Recommended battery mating pigtail, JST-PH male-header cable | 1 | $0.75 | [Adafruit 3814](https://www.adafruit.com/product/3814), **listed out of stock when checked**; use an equivalent verified mating lead from your supplies or supplier |
| Fine insulated wire, insulation, strain relief, filament | Small amount | Existing supplies | Budget separately if not already owned |
| USB-C data cable; multimeter; soldering tools | 1 each | Existing tools | A charge-only cable is insufficient for programming |

**Core electronics: $24.35. With the listed mating pigtail: $25.10**, before tax and shipping. The pigtail's listing is a price reference, not a claim of immediate availability. No second XIAO is required for this project, even though some manufacturer radio demonstrations use two. [S2, S5–S7, S18]

The selected battery's stated dimensions are 32.5 × 25 × 5 mm and it has factory protection and a JST-PH lead. Those dimensions, rather than the board footprint alone, set the enclosure's minimum size. [S5]

## 5. Electrical connections and charging

```text
Protected LiPo +  → mating lead → XIAO BAT+
Protected LiPo −  → mating lead → XIAO BAT− / GND
XIAO D0 / P1.00   → normally-open momentary switch → GND
Included antenna → board's IPEX4 connector
USB-C            → programming and charging
```

The external button uses the GPIO's internal pull-up. It is not connected to BAT+, 5 V, RESET or the microphone pins. Confirm the actual PCB's pad labels and switch contact pairs with a meter before soldering. [S1]

Although you are comfortable soldering, I am revising the earlier advice to remove the battery connector. Seeed warns against soldering an energized battery to the board. The recommended method is to solder the **unpowered board-side mating lead**, insulate it, check polarity, and connect the battery afterward. Never cut both battery conductors simultaneously or solder directly to pouch tabs. [S1, S5]

The intended firmware charge setting is **100 mA, 4.20 V termination**. Verify the generated device tree and measure the actual charging behavior before closing the enclosure. The selected battery permits no more than 350 mA charging; 100 mA is a conservative choice, not permission to omit testing. [S5, S8]

The manufacturer's schematic ties the PMIC NTC input to a fixed 10 kΩ resistor; the selected two-wire battery has no thermistor. **This build does not measure the cell's temperature.** Do not mistake the PMIC's die temperature or a fixed-resistor NTC reading for cell-temperature protection. Follow the battery supplier's handling and attended-charging instructions. [S4, S5]

## 6. Button operation

The external and onboard user buttons invoke the same firmware action. Two press edges within a nominal 500 ms window, with a release between them, confirm capture. A lone click briefly powers the microphone for the pre-buffer but does not create a stored message. Holding the button for 1.5 seconds opens a 60-second pairing window. A deliberate 10-second hold clears the Bluetooth bond without deleting recordings.

While a confirmed recording is active, one further click ends and saves it manually. This provides an escape when background noise prevents silence detection. Avoid a prolonged hold for normal capture so it is not confused with pairing controls.

The recessed printed button surround should reduce accidental activation without making a double-click awkward. Exact cap travel and contact force must be checked with the chosen switch.

## 7. Speed strategy and corrected power assumption

The original discussion proposed System OFF between recordings. The supplied first prototype instead uses **System-ON idle with the microphone off**, preserving the Bluetooth stack and program state. This makes development and rapid capture more predictable. Deep System OFF requires a reset/restart and a separate timed-wake strategy for automatic retry; it is not simply an interchangeable sleep command. [S9]

The first click enables the microphone and starts a short circular buffer. The second click commits capture, retaining up to 240 ms of pre-roll. A provisional 100 ms microphone-rail settling delay precedes capture; it must be measured and tuned on the actual board. There is no promise of literally zero startup latency.

Record the click-to-first-valid-sample time across repeated trials. The acceptance criterion is preservation of your first word when you speak naturally after the second click. Network connection speed is intentionally not an acceptance criterion for local capture.

## 8. Audio format and automatic stop

Capture is 16 kHz mono, 16-bit PCM. The portable codec converts each 320-sample, 20 ms frame to an independently decodable 164-byte IMA-ADPCM frame. Frames live in the documented **HVB1 container**, not a WAV/IMA block format. The Linux decoder produces ordinary PCM WAV for transcription.

The selected codec keeps microcontroller work simple and gives approximately 8,200 payload bytes per second: 492,000 bytes for a full minute plus a 64-byte header. This is a design calculation from the supplied protocol, not a manufacturer's compression claim.

The initial silence gate measures AC audio energy, requires approximately 60 ms of above-threshold activity, and ends after about 1.2 seconds below threshold once speech has begun. It discards a capture with no detected activity for five seconds and stops at a 60-second maximum. This is an **energy gate, not a language-aware speech recognizer**. Wind, music, quiet speech and long pauses must be tested and the threshold adjusted.

## 9. Recorder storage

The first 7.5 MiB of external flash is allocated to fifteen fixed 512 KiB recording slots. The final 512 KiB is reserved for Bluetooth/settings storage. A short recording still occupies one slot; the queue therefore holds **at most 15 messages**, not an unlimited number calculated from total audio bytes.

Completed files use a commit marker and payload checksum. Storage is not reclaimed until the Android receiver acknowledges a verified, durably saved copy. Erases occur incrementally while not recording. A full queue rejects a new capture rather than overwriting an old one. Red status requires attention.

**First installation owns and can erase that external-flash region.** Back up anything valuable from previous board firmware before flashing. A corrupted committed recording is retained rather than silently deleted; it may need explicit diagnostic recovery.

## 10. Bluetooth design

Use a private GATT service, not a headset profile. The recorder advertises when it has a completed recording or pairing is enabled. The phone requests a message ID/size, reads numbered positions in bounded chunks, verifies the full file, commits its private database, then acknowledges the ID.

BLE bonding uses a locally generated six-digit passkey and authenticated LE Secure Connections. Pairing is allowed only during the physical pairing window. The firmware never advertises the passkey. A stable BLE identity simplifies companion presence observation; it is a privacy tradeoff because nearby scanners can recognize that identity.

The first protocol uses request/response reads of up to 180 bytes, with an MTU request on Android. Transfer throughput is deliberately secondary to capture speed. A completed file may take noticeable time to move over BLE; you do not need to hold the button or wait for it.

## 11. Android application

The native Java app includes one setup/status screen, Companion Device Manager association, Bluetooth bonding, a `connectedDevice` foreground relay service and a private SQLite queue. It never requests `RECORD_AUDIO`, assistant-role access or an Accessibility Service.

Companion presence observation is Android's supported mechanism for recognizing an associated device in the background, but association itself does not establish a Bluetooth connection or bond. The app performs those separately. [S10–S12]

A foreground notification remains while the relay is enabled. The design supports locked-screen operation subject to actual Android/device behavior. Force-stopping the app, revoking permissions, turning Bluetooth off, or rebooting without the first unlock can prevent relay activity. Completed recordings stay on the device until the phone can receive them. These are explicit operating limitations, not failures to be disguised.

## 12. Feedback

The recorder has no speaker and no vibration motor. Its mechanical switch provides click feedback. Green indicates active confirmed capture; red indicates an error; blue is used during startup/pairing and magenta for bond reset. This prototype does not implement every color animation suggested in the earlier discussion.

The phone requests one vibration after committing a new recording and two short vibrations after a matching server acceptance. Haptics may be suppressed by phone settings, and their delay reflects delivery rather than microphone readiness. **Do not wait for a phone vibration before speaking.**

## 13. Tailscale transport

Prefer **HTTPS through Tailscale Serve**, terminating at the Hermes machine's full `.ts.net` name and forwarding to a loopback-only receiver on port 8765. The alternative is HTTP directly to that machine's Tailscale IPv4 on port 8765. A bearer token is required in both modes. [S13]

The app restricts the configured destination to these forms and refuses redirects. Tailscale must be running on both endpoints; no router port forwarding or public Funnel is part of this plan. Inspect any existing Serve configuration before changing it.

A sample grant limits phone-to-server access to the chosen TCP port. Grants are additive: a new narrow grant does **not** remove access already granted by a broader rule. Preserve necessary administration while reviewing the full policy. [S14]

## 14. Durable Linux receiver

A Python standard-library receiver authenticates the request, checks format and checksum, and commits the recording as a SQLite BLOB under FULL synchronization. Only then does it return `202 Accepted` with the same ID and SHA-256 hash. Duplicate uploads with identical bytes return the existing receipt; an ID with different bytes is rejected.

A 100 MiB default queue limit prevents uncontrolled growth of audio BLOBs; it is not a complete disk quota because transcripts, database overhead and worker logs also use space. The service is intended for a private single-user tailnet, not direct public exposure. It binds only to loopback or a Tailscale IPv4 address.

A separate worker means the phone does not wait for transcription or Hermes. Both services run as your ordinary Linux user, not root.

## 15. Transcription

The receiver worker decodes HVB1 to WAV and invokes a locally installed `whisper-cli`. The supplied preparation script uses a fixed whisper.cpp baseline and a multilingual `small` model; it is a reproducible starting choice, not a claim to be the newest or best model for your machine. [S15]

The model, executable and language are configurable. English/Spanish testing should include the words and names you actually use. Low-energy audio and noise can produce poor or invented transcripts, which is why commissioning starts in review mode. Original files remain available for inspection.

Local speech-to-text does not imply the entire Hermes system is offline: your existing Hermes model/provider may still need Internet access.

## 16. Hermes handoff

The adapter targets the documented **Nous Hermes Agent** command:

```sh
/path/to/hermes chat --query-file /path/to/prompt.txt
```

The worker checks that the installed command advertises `--query-file` before invoking it. This is a new standalone CLI query, not simulated typing into an existing chat and not an automatic continuation of whichever terminal session was last active. Your actual Hermes installation and notes workflow must be confirmed. [S16]

Subprocesses are launched with argument lists, not shell-expanded transcripts. The prompt identifies the recording and asks Hermes to follow normal confirmation rules for consequential actions. This is not a security sandbox; the agent retains whatever permissions its account already has.

## 17. Reliability and delivery semantics

Three durable handoffs are used: recorder to phone, phone to server, server to transcription/agent queue. The UUID follows the recording throughout the pipeline.

Network retries are idempotent. **Agent actions are not guaranteed exactly once.** If Hermes performs an action and the worker crashes before saving completion, blindly retrying can repeat that action. Such jobs become `uncertain` and require human review. This corrects the earlier overly broad statement that UUIDs alone prevent every duplicate Hermes action.

Successful speech-to-text is safe to retry after interruption. Agent dispatch is intentionally treated more cautiously. Message and receipt records are retained unless explicitly purged under the documented retention process.

## 18. Battery strategy

Use the onboard charger and a protected cell, with microphone power removed when idle. The device remains ready in System-ON idle for this first version. There is no implemented physical off switch; disconnect the battery for storage/service as appropriate.

Manufacturer microamp System OFF/Ship Mode figures describe particular test states, **not this firmware's measured current**. No weeks-to-months runtime promise is made. Measure idle, recording, connection/retry and charge currents, then estimate:

```text
Daily mAh = idle_mA × idle_hours
          + recording_mA × recording_hours
          + transfer_mA × transfer_hours
Approximate days = usable_battery_mAh / daily_mAh
```

Allow for temperature, cell aging, regulation losses and usable-capacity limits. Battery voltage is available in the protocol for diagnostics; a calibrated percentage/fuel-gauge user interface is not implemented.

## 19. Enclosure

Start from measured components, not the previous optimistic 36 × 28 mm sketch. Leave space for the battery, connector, board, switch supports and the full supplied antenna. `hardware/enclosure-worksheet.md` records dimensions and clearances before a final CAD design.

The microphone opening must align with the actual acoustic port and not be blocked by tape, the battery or a printed wall. Avoid a hole positioned from an unverified diagram. Keep the antenna against plastic and away from the cell and other metal as practical. Check real RF and acoustic performance with the lid installed.

Support the PCB and switch independently, provide lead strain relief, and never use the LiPo pouch as a spring or structural spacer. The first enclosure should be easy to reopen. No fit-validated STL is supplied because physical positions and the antenna envelope have not been measured.

## 20. Development and commissioning order

Build the receiver and run host tests first. Next establish USB-powered board operation, microphone capture and the private flash queue. Then prove encrypted BLE transfer on the bench before relying on Android background wake. Build/install the Android application and verify durable phone receipt. Add the Tailscale path and local transcription. Finally commission the actual Hermes adapter in review mode, then deliberately enable automatic processing.

Battery-current measurement and real locked-phone acceptance tests precede enclosure finalization. Deep-sleep optimization, streaming audio, a richer battery display and improved speech detection are later improvements, not hidden requirements to make the first capture-to-Hermes path understandable.

## 21. Acceptance criteria

A trustworthy daily-use build must preserve the first word, record with the phone absent, survive interrupted transfers, reject corrupted files, retain messages through network outages, and avoid replaying uncertain agent actions automatically. It must charge at the intended values, exhibit no abnormal heating, and have measured rather than guessed runtime.

Locked-screen tests must include a phone idle for at least 30 minutes, normal app process reclamation, Bluetooth off/on, loss of Tailscale, server outage and a reboot followed by first unlock. A force-stopped app is explicitly expected to require reopening; there is no promise to bypass Android's user stop decision. See the detailed checklist in `docs/06-testing.md`.

## 22. Deliverables and remaining configuration

The bundle contains this Markdown plan, a print manual, all custom firmware/Android/receiver source, configuration examples, wiring/BOM documentation, build helpers, test tools and a printable source listing. The installed SDKs, compilers, external model files, your existing Hermes credentials and compiled target binaries are separate requirements.

Your Tailscale address can be entered in setup without rebuilding the APK. The full hostname is preferable for HTTPS. Host OS, absolute Hermes command, phone version and final hardware measurements remain explicit blanks, not invented deployment facts.

**Recommended frozen prototype:** XIAO nRF54LM20A Sense; 350 mAh protected LiPo; external button; double-click; 16 kHz mono; HVB1/IMA-ADPCM; up to 15 queued 60-second messages; energy-based auto-stop; authenticated BLE; Android companion relay; Tailscale; durable Python queue; local Whisper; checked Hermes CLI adapter. Speaker: none. Assistant replacement: none.

## Sources

Source IDs refer to `SOURCES.md`, which gives the complete primary-source URLs and access date. The technical design, protocol and code are original project work; manufacturer claims, Android capabilities and product prices are identified separately.
