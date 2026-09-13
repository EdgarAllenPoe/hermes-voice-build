# Primary-source register

Accessed September 12, 2026. The URLs are printed in full so they remain usable from paper. Prices exclude tax and shipping. These source references support component/API facts, not measurements of this unbuilt system. External pages, SDKs and models are not bundled. Configuration choices, code and acceptance criteria are the project design, not claims that the cited manufacturers tested this project.

## [S1] Seeed XIAO nRF54LM20A getting started

https://wiki.seeedstudio.com/xiao_nrf54lm20a_getting_started/

Board dimensions, Sense microphone, battery interface and board pinout. Use the Sense SKU.

## [S2] Seeed product listing

https://www.seeedstudio.com/Seeed-Studio-XIAO-nRF54LM20A-Sense-p-6840.html

Listed board price $15.90; price/availability may change.

## [S3] Seeed Bluetooth and low-power examples

https://wiki.seeedstudio.com/xiao_nrf54lm20a_with_bluetooth_lowpower/

Supplied antenna, BLE setup, power-enable and controller-workaround examples.

## [S4] Seeed board schematic

https://files.seeedstudio.com/wiki/XIAO_nRF54LM20A/getting_start/RES/XIAO_nRF54LM20A_Schematic.pdf

Power schematic page 4 inspected: fixed thermistor resistor is not cell temperature measurement.

## [S5] Adafruit protected 350 mAh battery, product 4237

https://www.adafruit.com/product/4237

Listed $5.95; dimensions 32.5 × 25 × 5 mm; cell protection and charge guidance.

## [S6] Adafruit tactile switches, product 367

https://www.adafruit.com/product/367

Listed $2.50 for a 20-pack; use one switch.

## [S7] Seeed onboard microphone/BLE recording examples

https://wiki.seeedstudio.com/xiao_nrf54lm20a_with_onboard/

Reference microphone initialization and recording-transfer pattern; not a validation of this custom firmware.

## [S8] Zephyr nPM1300 charger binding

https://docs.zephyrproject.org/latest/build/dts/api/bindings/sensor/nordic,npm1300-charger.html

Device-tree configuration property meanings and permitted charge parameters.

## [S9] Seeed low-power examples

https://wiki.seeedstudio.com/xiao_nrf54lm20a_with_low_power/

System-OFF examples are distinct from the initial System-ON implementation in this package.

## [S10] Android background BLE communication

https://developer.android.com/develop/connectivity/bluetooth/ble/background

Background communication patterns and limitations; not a guarantee on an untested phone.

## [S11] Android companion-device pairing

https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing

Association flow and permissions; association does not itself establish a BLE connection.

## [S12] Android CompanionDeviceManager API

https://developer.android.com/reference/android/companion/CompanionDeviceManager

Device association and observation APIs, including newer presence observation.

## [S13] Tailscale Serve CLI

https://tailscale.com/docs/reference/tailscale-cli/serve

Private HTTPS reverse proxy and persistent background serving; inspect existing configuration first.

## [S14] Tailscale Grants

https://tailscale.com/docs/features/access-control/grants

Port-scoped network permissions; adding a narrow grant does not remove pre-existing broad grants.

## [S15] whisper.cpp upstream

https://github.com/ggml-org/whisper.cpp

Local transcription integration. The helper uses a chosen version baseline, not a claim of the latest release.

## [S16] Nous Research Hermes CLI guide

https://hermes-agent.nousresearch.com/docs/user-guide/cli/

Documented chat --query-file adapter. Confirm that the installed Hermes product and version support it.

## [S17] Seeed PlatformIO board platform

https://github.com/Seeed-Studio/platform-seeedboards

Vendor board support used by the uncompiled firmware target; freeze actual working versions after a successful build.

## [S18] Adafruit mating battery lead, product 3814

https://www.adafruit.com/product/3814

Listed $0.75 but out of stock when checked. Obtain an equivalent verified mating lead or use existing supplies.

## [S19] Android Gradle Plugin 8.10 release notes

https://developer.android.com/build/releases/agp-8-10-0-release-notes

Chosen AGP 8.10.1 / Gradle 8.11.1 / JDK 17 baseline; supports API 36. Not represented as the latest toolchain.

## [S20] Gradle distribution and published checksum

https://services.gradle.org/distributions/gradle-8.11.1-bin.zip

Bootstrap helper also retrieves the same URL with .sha256 appended and verifies the download.

## [S21] Android DevicePresenceEvent API

https://developer.android.com/reference/android/companion/DevicePresenceEvent

API 36 device-presence event handling in the Android source.

## [S22] Upstream Zephyr board device tree

https://raw.githubusercontent.com/zephyrproject-rtos/zephyr/main/boards/seeed/xiao_nrf54lm20a/xiao_nrf54lm20a_nrf54lm20a-common.dtsi

Cross-check of charger hierarchy. Upstream and vendor board-support labels/settings differ; this is not a substitute for verifying the generated build device tree.
