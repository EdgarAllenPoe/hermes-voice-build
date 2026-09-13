# Public source file index

This index describes the complete source checkout. Compiled delivery binaries, generated private configuration, signing keys, receiver state, and user recordings belong outside the tracked source.

Start with [README.md](README.md). Historical reports are labelled and retained for provenance; see [VERIFIED_BUILD.md](VERIFIED_BUILD.md) for the selected compiled kit.

## Root

- `.gitattributes`
- `.gitignore`
- `BLE_BUILD_FIX.md`
- `BOARD_INTEGRATION_NOTES.md`
- `BUILD_NOTES.md`
- `Build-All.ps1`
- `CHANGELOG.md`
- `FILE_INDEX.md`
- `HANDOVER-2026-09-13.md`
- `INSTALL_COMPILED_KIT.md`
- `LICENSE.txt`
- `PRINT_ME_FIRST.md`
- `PROJECT_PLAN.md`
- `README.md`
- `SCONS_ADAPTER_FIX.md`
- `SHA256SUMS.txt`
- `SOURCES.md`
- `START_HERE_BUILD_STATUS.md`
- `TEST_REPORT.md`
- `TEST_REPORT_v0.2.md`
- `USER_CONFIGURATION.md`
- `VERIFIED_BUILD.md`
- `build-all.sh`

## .github

- `.github/workflows/build.yml`
- `.github/workflows/firmware-retry.yml`
- `.github/workflows/host-tests.yml`

## android

- `android/app/build.gradle`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/org/tomstout/hermesvoice/BootReceiver.java`
- `android/app/src/main/java/org/tomstout/hermesvoice/CompanionService.java`
- `android/app/src/main/java/org/tomstout/hermesvoice/Endpoint.java`
- `android/app/src/main/java/org/tomstout/hermesvoice/Feedback.java`
- `android/app/src/main/java/org/tomstout/hermesvoice/MainActivity.java`
- `android/app/src/main/java/org/tomstout/hermesvoice/QueueDb.java`
- `android/app/src/main/java/org/tomstout/hermesvoice/RelayService.java`
- `android/app/src/main/java/org/tomstout/hermesvoice/Settings.java`
- `android/app/src/main/java/org/tomstout/hermesvoice/UploadJob.java`
- `android/app/src/main/java/org/tomstout/hermesvoice/Uploader.java`
- `android/app/src/main/java/org/tomstout/hermesvoice/Wire.java`
- `android/app/src/main/res/drawable/ic_voice.xml`
- `android/app/src/main/res/xml/network_security_config.xml`
- `android/build.gradle`
- `android/gradle.properties`
- `android/settings.gradle`

## config

- `config/build-recipient.crt.pem`
- `config/build-targets.json`
- `config/tailscale-grant.example.hujson`

## docs

- `docs/01-hardware.md`
- `docs/02-firmware.md`
- `docs/03-android.md`
- `docs/04-server.md`
- `docs/05-protocol.md`
- `docs/06-testing.md`
- `docs/07-offline.md`
- `docs/08-troubleshooting.md`
- `docs/09-security.md`
- `docs/host-tests.txt`

## firmware

- `firmware/patch_pio_zephyr.py`
- `firmware/platformio.ini`
- `firmware/src/ble.c`
- `firmware/src/codec.c`
- `firmware/src/codec.h`
- `firmware/src/device_config.h`
- `firmware/src/hvb.h`
- `firmware/src/main.c`
- `firmware/src/storage.c`
- `firmware/zephyr/CMakeLists.txt`
- `firmware/zephyr/app.overlay`
- `firmware/zephyr/charger.overlay`
- `firmware/zephyr/prj.conf`

## fixtures

- `fixtures/README.md`
- `fixtures/synthetic-tone.hvb`
- `fixtures/synthetic-tone.wav`

## hardware

- `hardware/enclosure-worksheet.md`
- `hardware/shopping-list.csv`
- `hardware/wiring.txt`

## receiver

- `receiver/config.example.json`
- `receiver/config.tomstout.json`
- `receiver/hvbridge/__init__.py`
- `receiver/hvbridge/__main__.py`
- `receiver/hvbridge/audio.py`
- `receiver/hvbridge/server.py`
- `receiver/hvbridge/storage.py`
- `receiver/hvbridge/worker.py`
- `receiver/install-user.sh`
- `receiver/requirements.txt`
- `receiver/systemd/hermes-voice-receiver.service`
- `receiver/systemd/hermes-voice-worker.service`

## tests

- `tests/WireTest.java`
- `tests/host_shim/storage_harness.c`
- `tests/host_shim/zephyr/device.h`
- `tests/host_shim/zephyr/drivers/flash.h`
- `tests/host_shim/zephyr/kernel.h`
- `tests/host_shim/zephyr/random/random.h`
- `tests/host_shim/zephyr/sys/atomic.h`
- `tests/host_shim/zephyr/sys/byteorder.h`
- `tests/host_shim/zephyr/sys/crc.h`
- `tests/test_audio.py`
- `tests/test_build_package.py`
- `tests/test_flash_simulation.py`
- `tests/test_java_protocol.py`
- `tests/test_manifest.py`
- `tests/test_native_codec.py`
- `tests/test_pio_adapter_patch.py`
- `tests/test_storage_http.py`
- `tests/test_worker.py`

## tools

- `tools/audio_tool.py`
- `tools/bootstrap-gradle.sh`
- `tools/build-android.ps1`
- `tools/build-android.sh`
- `tools/build_binaries.py`
- `tools/check_firmware_dts.py`
- `tools/ci_package.py`
- `tools/desktop_ble_receiver.py`
- `tools/freeze-environment.sh`
- `tools/prepare-whisper.sh`
- `tools/provision.py`
- `tools/run_tests.sh`
- `tools/send_recording.py`
- `tools/verify_package.py`
