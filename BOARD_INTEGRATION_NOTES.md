# Target-build integration corrections

These are changes required by actual cloud builds, not claims of physical-device testing.

## Zephyr 4.4 secure randomness

The first firmware build rejected `CONFIG_CSPRNG_ENABLED=y` because that symbol is derived and has no user prompt. The application now requests `CONFIG_CSPRNG_NEEDED=y` and `CONFIG_HARDWARE_DEVICE_CS_GENERATOR=y`. The generated Kconfig preflight requires the resulting CSPRNG_ENABLED and hardware generator to be enabled and rejects insecure test RNGs. Source: Zephyr v4.4.0 `subsys/random/Kconfig`.

## Seeed power-rail correction

The pinned vendor platform removed the old `power_en` node. Seeed's September 11, 2026 implementation note says that P1.12 is unconnected on the schematics and the old node was erroneous, not an actual system-power switch. It also moves nPM1300 regulator initialization after charger initialization so the configured USB current limit takes effect before LDO1 inrush.

The application now checks/enables the real `vsys_3v3` BUCK2 supply instead of referencing the removed GPIO regulator. Generated DTS from the first actual build confirms that BUCK2 is always-on at 3.3 V and that `dmic_vdd` is the LDO1 microphone rail. The existing overlay continues to remove microphone boot-on so capture controls that rail. Charger settings remain 100 mA / 4.20 V with the configured 500 mA USB input limit. The charger driver is not disabled.

The new preflight requires these rail values, partition boundaries, the P1.00 button, secure BLE/RNG, and charger-before-regulator initialization. Nine local validator checks used the actual generated DTS and an explicitly synthetic Kconfig fixture, including rejection of unsafe changes; they are not a target or hardware test.

Vendor record at the exact pinned revision:
https://github.com/Seeed-Studio/platform-seeedboards/blob/1ec1287f8e4bc4067a6fd593991e36875aef989f/.agents/notes/implemented/2026-09-11-npm1300-ldo1-power-sequencing.md

## Android CI signing backup

The APK compiled and passed apksigner verification after locating sdkmanager by its full SDK path. The runner did not store its initial default debug key at the assumed home-directory path. CI now explicitly creates and uses a private debug key at `HVB_DEBUG_KEYSTORE` and encrypts its backup before upload. The final APK must be retained together with the matching private signing-key backup for compatible future updates. Generated private keys are never committed.
