# 02 — Firmware build, board integration and controls

## Target and toolchain

This source targets **XIAO nRF54LM20A Sense using Seeed's PlatformIO Zephyr board package**. It is not an Arduino sketch and not firmware for the nRF52840 or nRF54L15. Platform/board sources and the current manufacturer examples are listed in [S1, S3, S9, S17]. The target build was not executed in the authoring environment.

Install Python 3, Git, PlatformIO Core and the required USB permissions/drivers while online. Keep the manufacturer package downloaded for offline use. On a Linux build workstation:

```sh
python3 -m venv ~/.venvs/hermes-build
. ~/.venvs/hermes-build/bin/activate
python -m pip install --upgrade pip platformio
cd /path/to/Hermes-Voice-Button-v0.1
python3 tools/provision.py
pio run -d firmware
```

`platformio.ini` selects environment `seeed-xiao-nrf54lm20a` and framework `zephyr`. The platform Git URL intentionally follows the vendor distribution because a validated commit is not yet known here. **After a successful build**, record and pin the installed platform commit, package versions and firmware hashes. Do not describe a moving Git branch as a locked dependency. Guide 07 covers offline caching.

There is no shared shipping BLE passkey. `device_config.h` initially contains `#error`; provisioning replaces it with a locally random code. Print `config/private/pairing-card.txt` privately and retain it. Re-running provision requires `--force`, rotates the code and requires re-pairing. Do not commit the generated header or pairing card to a public repository.

The pristine-package test that expects `#error` is supposed to stop passing once you have provisioned your real device. Run that test before provisioning, or exclude `test_no_default_pairing_secret` when testing a locally configured build. Do not revert your code to a shared test secret.

## Mandatory board-support checks

The supplied firmware expects these device-tree labels/aliases:

| Label/alias | Intended device |
|---|---|
| `pdm20` | PDM microphone controller |
| `dmic_vdd` | Switchable microphone rail, intended 3.3 V |
| `power_en` | Board power-enable regulator |
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

## Flashing and first boot

With the battery disconnected and the supplied antenna attached:

```sh
pio run -d firmware -t upload
```

Use the upload method supplied by the installed vendor package for this board. Do not erase the external flash blindly to fix an unrelated compiler problem. The firmware itself claims the recording partition; first installation is destructive to unrelated prior contents there.

Initially test the onboard user button. Confirm microphone rail behavior, usable audio, no short circuits and expected LED response before connecting the external button or battery. For troubleshooting, use a debugger or temporarily enable a verified vendor console configuration; the normal build disables serial console output. No successful flash or boot has been claimed for this bundle.

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

`main.c` handles buttons, microphone setup, buffering, gating and the background maintenance loop. `codec.c` is a portable ADPCM encoder/decoder and energy gate. `storage.c` manages commit-last flash slots and erase-after-ACK. `ble.c` exposes the authenticated GATT protocol. `device_config.h` supplies the locally generated pairing code. `prj.conf` and overlays define board integration.

Idle is System ON, not System OFF. A microphone rail and radio connection are not assumed to become zero-power just because the main loop sleeps. Do not advertise a battery-life estimate until measuring the finished firmware.

## Queue behavior and recovery

There are 15 slots. Red on capture can mean no free slot, microphone/flash initialization failure or another capture error; it is not a detailed error code. Check phone queue status and BLE bench diagnostics before deleting anything. A partial power-interrupted capture is not exposed as a committed message.

A committed file that fails the phone's checksum is not acknowledged. The phone discards its partial download and retries, while the recorder keeps the original. Repeated identical failures suggest corrupt flash or a protocol bug; preserve the device state for diagnosis instead of adding an automatic “delete bad message” shortcut.

## Board power enable

The application explicitly enables the vendor `power_en` regulator and allows 20 ms settling before BLE initialization, following the manufacturer board example [S3]. The microphone rail is managed separately. If the selected board package lacks either node label, reconcile that board-support integration rather than commenting out the checks.
