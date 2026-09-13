# 01 — Hardware assembly and power commissioning

## Parts and wiring

Use the Sense variant. Verify its label and inspect the supplied antenna before assembly. The wiring netlist is also in `hardware/wiring.txt`. The purchase worksheet is `hardware/shopping-list.csv`.

| Net | Connection | Check before power |
|---|---|---|
| Battery positive | Protected cell positive, through mating lead, to BAT+ | Meter the actual connector polarity; do not assume all JST cables agree |
| Battery negative | Protected cell negative to BAT− / GND | Continuity to board GND |
| Capture switch | D0 / P1.00 to one switch contact, opposite contact to GND | Open circuit released, near zero ohms pressed |
| Antenna | Supplied FPC antenna to IPEX4 | Align straight down; do not force a different connector size |
| USB | Board USB-C | Data-capable cable for flashing |

On a four-leg tactile switch, two legs on the same side can already be connected. Select the two electrical contact groups using a continuity meter, not appearance. The GPIO internal pull-up means no separate pull-up component is required in this design.

The relevant board pin and supply information is documented by Seeed [S1–S4]. It does not validate your particular solder joints or a later PCB revision.

## Recommended assembly sequence

1. Photograph the board's markings. Record its revision in `USER_CONFIGURATION.md`. Inspect for damage and compare BAT pads, D0 and GND with the board reference.
2. Keep the battery disconnected. Plug in USB and establish that the unmodified board is recognized. Do not connect additional power sources to 3V3 or 5V pads.
3. Remove USB. Solder switch leads and the **unpowered mating battery pigtail**. Trim the pigtail to a useful service length. Insulate solder joints and anchor wires so tension is not borne by pads.
4. Use a meter to check for BAT+/GND shorts and correct connector polarity. A continuity check cannot prove every regulator or charger is healthy; it is one preliminary check.
5. Program and test the new firmware using USB only. Run the generated-device-tree preflight described in guide 02.
6. Only after the firmware charge profile is verified, attach the protected cell for a supervised open-enclosure test. Stop on heating, odor, swelling, unstable operation or unexpected current.
7. Test recording and BLE on battery alone. Then test USB insertion/removal and charging without corrupting recordings. Do not deliberately deep-discharge or short the battery for failure testing.
8. Fit the enclosure without pinching wires, compressing the pouch or covering the microphone. Recheck audio and radio range with the lid installed.

This preserves your ability to solder directly to the board without soldering an energized cell into a live circuit. The added connector also makes servicing and current measurement easier. The listed Adafruit 3814 mating pigtail was out of stock when checked; an electrically verified equivalent or an existing lead is acceptable [S18].

## Charger requirements

The overlay sets 100,000 µA charging current and 4,200,000 µV termination for this ordinary 1S LiPo. **Do not use a 4.35 V high-voltage cell profile, LiFePO4, a multi-cell pack, an unprotected loose cell or an RC pack.** The selected battery's supplier specifies its protection and charging limits [S5]; the driver property definitions are in [S8].

Inspect the generated `zephyr.dts`, not just the text of `charger.overlay`. A board-support update can change the effective configuration. Verify there is one active nPM1300 charger node and no conflicting overlay. If a built-in default overrides the requested values, correct the board integration before connecting the cell.

Use a suitable inline current instrument on the **battery connection** to measure charging current. A USB input reading also includes board consumption and is not by itself cell-charge current. Follow your meter's fuse/range instructions. Never place a meter in current mode directly across the battery.

Check termination against an accurate voltmeter and the cell's specification, allowing the charger taper to complete; a brief idle voltage reading does not establish a successful full charge. Follow the cell vendor's permitted temperature range. The prototype is for attended indoor charging, not charging unattended in a pocket, vehicle or sealed warm case.

The schematic's NTC is a fixed resistor, and this cell has no temperature lead. This implementation therefore lacks actual pack-temperature measurement. Do not represent it as a fully temperature-protected commercial charger. Adding a real cell thermistor would require a reviewed hardware modification and matching configuration; that modification is not included here [S4, S5].

## Power measurement worksheet

| Measurement | Result |
|---|---|
| Battery voltage before test | ______ V |
| Idle with no recordings queued | ______ mA |
| Active microphone/flash recording | ______ mA |
| BLE transfer to nearby phone | ______ mA |
| Pending queue with absent phone | ______ mA |
| Cell charge current at low/mid state of charge | ______ mA |
| Charge termination voltage | ______ V |
| Unusual temperature rise | None / Investigate |
| Completed captures per day | ______ |
| Estimated/observed runtime | ______ |

No measured runtime is supplied in this package. System OFF manufacturer figures are not applicable to the supplied System-ON prototype.
