# 02 — Firmware build, board integration and controls

## Target and toolchain

This source targets **XIAO nRF54LM20A Sense using Seeed's PlatformIO Zephyr board package**. It is not an Arduino sketch and not firmware for the nRF52840 or nRF54L15. Platform/board sources and the current manufacturer examples are listed in [S1, S3, S9, S17]. Both targets subsequently compiled in the selected run `34752997335`; see `../VERIFIED_BUILD.md`. Physical board acceptance is still outstanding.

Install Python 3, Git, PlatformIO Core and the required USB permissions/drivers while online. Keep the manufacturer package downloaded for offline use. On a Linux build workstation:

```sh
python3 -m venv ~/.venvs/hermes-build
. ~/.venvs/hermes-build/bin/activate
python -m pip install "platformio==6.1.18"
cd /path/to/hermes-voice-build
python3 tools/build_binaries.py --target firmware
```

`platformio.ini` selects environment `seeed-xiao-nrf54lm20a` and framework `zephyr`. The vendor platform is pinned to commit `1ec1287f8e4bc4067a6fd593991e36875aef989f`, used by the selected compiled build. Keep the resolved package versions and generated hardware configuration with each later build; the pin does not lock every transitive dependency. Guide 07 covers offline caching.

For the personal recorder, first restore the retained identity as described in [guide 10](10-prehardware.md). A fresh identity is appropriate only for a new recorder, not an accidental replacement of the existing pairing pair.

There is no shared shipping BLE passkey. The tracked `firmware/src/device_config.example.h` contains only a build-error guard. Provisioning creates `firmware/src/device_config.h` with a locally random code; that generated file is ignored and is not tracked. A fresh checkout does not contain it. The build helper automatically provisions a fresh checkout when needed. Print `config/private/pairing-card.txt` privately and retain it. Re-running provision requires `--force`, rotates the code and requires re-pairing. Do not commit the generated header or pairing card to a public repository.

The no-default-passkey test inspects the public example, so the suite can run after local provisioning without reading or replacing a real passkey. Keep a matched header and pairing card together. If only one survives, restore its matching counterpart before building; do not rotate it accidentally.

## Mandatory board-support checks

The supplied firmware expects these device-tree labels/aliases:

| Label/alias | Intended device |
|---|---|
| `pdm20` | PDM microphone controller |
| `dmic_vdd` | Switchable microphone rail, intended 3.3 V |
| `vsys_3v3` | Actual system rail (BUCK2), replacing the obsolete `power_en` assumption |
| `py25q64` | 8 MB external SPI NOR flash |
| `pmic` and its charger child | nPM1300 |
| `sw0` | Onboard user button |
| `led0`, `led1`, `led2` | Blue, red, green |
| `voice-button` | External D0/P1.00 input |

Seeed's examples use custom rail labels that are not necessarily identical to upstream Zephyr's current board definition. **A missing label is a board-support mismatch, not permission to guess a substitute rail or disable an error.** Inspect your installed board files, schematic and the generated DTS. Resolve compiler/Kconfig errors against that exact vendor package before flashing.

Find the resulting device tree:

```sh
find firmware/.pio -name zephyr.dts -print
python3 tools/check_firmware_dts.py /actual/path/to/zephyr.dts
```

Then manually check charger current 100 mA, termination 4.20 V, the 3.3 V microphone rail, button GPIO and flash partitions. The script checks a subset; it is not a complete hardware validator. Check flash/settings overlap and the generated `.config` as well. Preserve both with the build record.

The BLE compiler workaround flags included with this project come from Seeed's current example configuration [S3]. If a later vendor release removes a symbol, inspect the new release rather than suppressing unrelated configuration failures. `CONFIG_ARM_MPU=n` is a vendor-example workaround, not a general security recommendation for every future release.

## Pairing-storage geometry

The SPI NOR driver defaults to 64 KiB layout pages. Zephyr NVS stores its sector size in a 16-bit field and rejects that default with `-EDOM`; Bluetooth cannot initialize its pairing-settings backend. The first physical board exposed this failure after the image had flashed and verified correctly.

The firmware explicitly selects `CONFIG_SPI_NOR_FLASH_LAYOUT_PAGE_SIZE=4096`. This uses the driver's supported 4 KiB sector layout without changing the audio or settings partition boundaries. The preflight checker now rejects the 64 KiB default, an overflowing sector multiplier, or a sector count that does not fit the settings partition.

On Windows, the build helper supplies UTF-8 to its Python subprocesses and enables Git long-path support only for its build commands. This prevents failed vendor-library checkouts and dependency-report encoding errors without modifying global Git settings.

## Flashing and first boot

Use the explicit prebuilt-bundle helper described in the [printable workshop guide](HARDWARE-GUIDE.md). Keep the battery disconnected and attach the supplied antenna. Replace the path below with the exact successful timestamped build directory:

```sh
python3 tools/flash_prebuilt.py --bundle /absolute/path/to/successful/dist-folder
python3 tools/flash_prebuilt.py --bundle /absolute/path/to/successful/dist-folder --flash
```

The first command only verifies hashes and generated configuration; it does not access hardware. The second verifies the exact vendor loader, disables its automatic mass-erase recovery hook, and requires the displayed typed confirmation before writing and verifying the HEX. Install the pinned PlatformIO OpenOCD 3.1200.x package as described in the workshop guide.

Preserve wanted recordings and any prior external-flash data before programming. Stop on a locked or unrecognized board instead of using a guessed recovery command. On first use, the application claims its external-flash recording partition. The first physical flash and application initialization are recorded in [guide 12](12-first-flash.md). Continue with USB-only functional tests; microphone, phone pairing, and battery/charger acceptance remain outstanding.

For troubleshooting, use a debugger or temporarily enable a verified vendor console configuration; the normal build disables serial console output.

## Controls

| Gesture | Action |
|---|---|
| Single click while idle | Tentative pre-buffer only; discarded without a second click |
| Double-click within about 500 ms | Record, retain pre-roll, green LED |
| Single click while recording | Finish/save immediately; manual-stop flag |
| Speak then pause about 1.2 s | Automatic finish after energy gate has recognized activity |
| No detected activity for 5 s | Discard tentative speech capture |
| Continuous recording reaches 60 s | Finish with maximum-duration flag |
| Hold at least 1.5 s while idle | Open pairing for 60 s; release to pair |
| Hold at least 10 s while idle | Forget old phone bond; recorded messages remain |

The 60-second limit includes pre-roll. The energy gate is deliberately simple; a loud environment can hold the recorder open until the limit. The absence of the phone does not prevent local recording when free slots exist.

## Code map

`main.c` handles microphone setup, buffering, gating and the background maintenance loop. `button.c` implements the tested gesture timing. `codec.c` is a portable ADPCM encoder/decoder and energy gate. `storage.c` manages commit-last flash slots and erase-after-ACK. `ble.c` exposes the authenticated GATT protocol. `device_config.h` supplies the locally generated pairing code. `prj.conf` and overlays define board integration.

Idle is System ON, not System OFF. A microphone rail and radio connection are not assumed to become zero-power just because the main loop sleeps. Do not advertise a battery-life estimate until measuring the finished firmware.

## Queue behavior and recovery

There are 15 slots. Red on capture can mean no free slot, microphone/flash initialization failure or another capture error; it is not a detailed error code. Check phone queue status and BLE bench diagnostics before deleting anything. A partial power-interrupted capture is not exposed as a committed message.

A committed file that fails the phone's checksum is not acknowledged. Version 0.3 preserves an invalid completed download privately for diagnosis and retries while the recorder keeps the original. After three failures, supported firmware can skip that recording for the current connection so later recordings can transfer; this does not ACK or erase the bad recording. Repeated identical failures suggest corrupt flash or a protocol bug; preserve the device state for diagnosis instead of adding an automatic “delete bad message” shortcut.

## Board power enable

The application explicitly enables the vendor `vsys_3v3` regulator and allows 20 ms settling before BLE initialization, following the manufacturer board example [S3]. The microphone rail is managed separately. If the selected board package lacks either node label, reconcile that board-support integration rather than commenting out the checks.

Version 0.3 moves button timing to button.c, validates committed CRCs on boot, and reports quarantined slots through INFO. Guide 10 describes non-destructive SKIP and the new counters.
