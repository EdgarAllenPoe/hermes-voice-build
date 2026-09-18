# First physical flash and startup verification

On 18 September 2026, the personal v0.3.0 firmware was programmed onto the connected XIAO nRF54LM20A using its onboard CMSIS-DAP adapter. The corrected image contains 197,116 program bytes. OpenOCD verified all programmed bytes against the HEX, reset the processor, and exited successfully.

## Startup correction

The initial image programmed correctly but stopped during Bluetooth initialization. A hardware breakpoint showed that bt_enable returned -EDOM (-33). The SPI NOR driver's default 65,536-byte layout page exceeds the 16-bit sector-size field used by Zephyr NVS, so the Bluetooth settings backend cannot mount.

The corrected configuration selects CONFIG_SPI_NOR_FLASH_LAYOUT_PAGE_SIZE=4096. It preserves the existing recording/settings partition boundaries and the retained personal pairing code. Preflight validation now rejects incompatible NVS geometry before programming.

After reflashing, a hardware breakpoint confirmed that hvb_ble_init returned zero. A second breakpoint confirmed that main reached the completed background-thread creation call. Debug breakpoints were removed and execution resumed. This verifies application initialization, including the storage and Bluetooth setup required to reach that point.

## Validation and tooling

- Personal firmware clean build completed on Windows with PlatformIO 6.1.18, the pinned Seeed platform commit 1ec1287f8e4bc4067a6fd593991e36875aef989f, and Zephyr package 3.40400.260428.
- Generated DTS/Kconfig, NVS geometry, Intel HEX, ARM ELF, and artifact checksums passed.
- OpenOCD package 3.1200.7 programmed and verified the corrected image.
- Six NVS regression tests, eight build-helper tests, and seven prebuilt-flash tests passed.
- The original faulty bundle is rejected by the updated preflight checker.
- The build helper now uses UTF-8 output and scopes Git long-path support to Windows build commands.

The initial board had debug access protection enabled and erase protection disabled. Recovery used the exact pinned vendor CTRL-AP routine with the selected adapter; automatic recovery remained disabled. Later programming of the corrected image required no additional recovery. Recovery is a distinct, destructive operation and is not enabled automatically by the normal prebuilt-flash helper.

The private delivery folder firmware-v0.3 retains the tested HEX/BIN/ELF, generated configuration, matching private pairing card, and programming/startup logs. Do not publish the private firmware bundle.

## Remaining hardware acceptance

Phone pairing, microphone capture, recording transfer, speech quality, power-interruption recovery, and electrical charger/battery measurements remain to be performed. Successful programming and initialization do not certify those functions. Continue with USB power and the hardware guide's commissioning procedure.
