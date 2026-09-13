> **Historical record:** The original text below describes an earlier authoring session. Both targets subsequently compiled in run `34752997335`; see [README.md](README.md) and [VERIFIED_BUILD.md](VERIFIED_BUILD.md) for current status. Physical hardware acceptance remains outstanding.

> **v0.2 SOURCE-ONLY UPDATE:** No APK, HEX, BIN, or ELF was built in this session. Read `START_HERE_BUILD_STATUS.md` first. The text below is retained from the v0.1 source package; old print-file references are not included in this update.

# Print me first
## Hermes Voice Button • v0.1

**Daily goal:** double-click the small device, speak, stop speaking, put it away. The phone remains locked. No assistant replacement. No phone microphone.

**Read this as a prototype build package.** Host tests passed; target binaries and hardware behavior still need verification. Do not connect the LiPo before reviewing the charger setup. Do not rely on the device for important ideas until the acceptance tests pass.

## Build order

1. Save and unzip the complete package. Keep an untouched backup. Verify `SHA256SUMS.txt` with `tools/verify_package.py`.
2. Print `print/Hermes-Voice-Button-Manual.pdf`. Use `print/Hermes-Voice-Button-Source.pdf` or `print/Source-Listing.html` for offline reading or printing of all programming files.
3. Complete `USER_CONFIGURATION.md`. Obtain the board, battery, switch and optional battery mating connector.
4. While online, install/cache the Android SDK, JDK, Gradle, PlatformIO board tools and Whisper model. Prove an offline rebuild before leaving Internet access behind.
5. Run the host tests. Generate your own private BLE pairing code with `tools/provision.py`.
6. Build firmware. Inspect its generated device tree and charger values. Flash and test using USB power first. Then complete battery checks.
7. Install the Linux receiver in review mode. Prove a test file is durably accepted. Configure the actual Hermes command; do not assume an interactive terminal session will receive the message.
8. Build/install the Android app, configure the server endpoint/token, associate and bond the recorder, then enable the relay.
9. Test offline capture, locked-phone transfer, failures, first-word capture, charging and current draw. Only then enable automatic Hermes delivery and finalize the enclosure.

## Stop conditions

Stop if the battery is damaged or heats unexpectedly, charger current/voltage is wrong, firmware labels do not match the actual board, a target build fails, the phone ACKs a file it did not save, or a message disappears during failure tests.

**One phone vibration:** Android has saved the recording. **Two short vibrations:** the Hermes computer has accepted it. Neither means Hermes has finished processing. The recorder itself does not vibrate.

**No delivery vibration is not proof of recording failure.** Bluetooth or Tailscale may be unavailable; inspect the queues. The recorder has space for at most 15 committed messages in this prototype.
