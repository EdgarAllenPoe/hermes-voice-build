# Hermes Voice Button

Android relay application and recorder firmware for the Seeed Studio XIAO nRF54LM20A Sense.

## Build status

Repository initialization only. The Android APK and microcontroller firmware have not yet been compiled or validated in this repository. A successful source upload or host-test run is not a successful target build.

The project uses the configured v0.2 source package and its existing build and commissioning requirements. The original target is the XIAO nRF54LM20A Sense, not a similarly named nRF54L15 or nRF52840 board.

## Privacy and commissioning

Keep generated BLE pairing codes, server tokens, Android signing keys, and recordings out of this public repository. Personal firmware artifacts containing a private pairing code must not be published unencrypted.

Before battery operation, follow the project manual: test with USB power first and the battery disconnected, verify the generated device tree, and measure actual charging behavior. A successful compilation does not establish safe charging or working hardware.
