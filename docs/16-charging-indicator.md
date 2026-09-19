# Charging indicator in firmware 0.3.3

The board has two separate lights. The RGB recorder light still turns green for recording and blue for pairing. The small dedicated red charging light, D2 on nPM1300 LEDDRV1, now indicates measured charging current.

| Dedicated charging LED | Meaning |
| --- | --- |
| Steady red | USB is present and at least 10 mA is flowing into the cell |
| Off after charging | Current has stopped or tapered below the 10 mA indication threshold |
| Off in other circumstances | USB/battery absent, charging paused/faulted, or readings unavailable |

Allow about one second for changes. Off alone cannot establish that the battery is full. For a normal full-charge check, leave USB attached and confirm that the light goes out after charging; corroborate with cell voltage/current and charger diagnostics if uncertain. Voltage alone is not a reliable percentage indicator.

## Implementation

The Zephyr nPM13xx LED driver initializes LEDDRV1 in host mode. A single low-priority background task owns charger sampling, updates the existing battery-voltage value and sets the dedicated LED independently of RGB controls. Sampling continues during recording. A failed sensor read requests LED-off; a failed LED write is retried on the next sample and retained in a debugger-readable error counter.

The policy follows [Seeed's exact-board example](https://github.com/Seeed-Studio/platform-seeedboards/tree/main/examples/zephyr-npm1300-register-read): USB present, voltage at least 2.0 V and positive current of at least 10% of the configured charging current. It deliberately avoids relying on the D00 COMPLETE flag. Additional checks suppress indication for charger errors, thermal pause, USB over/undervoltage and suspended USB input. No charger faults or safety timers are cleared. The threshold is an indication heuristic, not a new charging termination rule.

Charging remains controlled by the PMIC at the existing 100 mA / 4.20 V settings. The fixed NTC resistor is not a battery-temperature sensor. No phone app update or new Bluetooth pairing is required.

Sources: [Seeed board documentation](https://wiki.seeedstudio.com/xiao_nrf54lm20a_getting_started/), [board schematic](https://files.seeedstudio.com/wiki/XIAO_nRF54LM20A/getting_start/RES/XIAO_nRF54LM20A_Schematic.pdf), and [Nordic nPM1300 specification](https://docs-be.nordicsemi.com/bundle/nPM1300_PS_v1.1/raw/resource/enus/nPM1300_PS_v1.1.pdf).

## Validation and remaining physical checks

Eight tests compile and exercise the production C indication policy, including the measured battery sample, trickle/taper boundary, completed charge, unreliable COMPLETE flags, unplugged USB, missing battery, discharge, faults, thermal pause and current-limited USB. Existing button and audio tests also pass.

Before this update, three PMIC samples showed 4.018-4.023 V, about 104 mA charging, USB present and no charger error. This establishes charging at that time, not a completed charge cycle.

After installation, visually confirm the separate red light while charging. Record a harmless phrase to check green recording indication and delivery while the charging light stays independent. Full-charge turn-off and battery-only operation remain physical acceptance checks.
