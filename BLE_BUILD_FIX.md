# Zephyr 4.4 Bluetooth compilation fixes

Run 34738237677 reached actual ARM C compilation after the PlatformIO adapter correction. It found two fatal errors in the original advertising setup.

1. Function-local static advertising descriptors used BT_DATA_BYTES, whose compound-literal payload arrays do not have static storage duration at block scope. The descriptors were moved to file scope so their payloads are valid static initializers and remain alive throughout advertising.
2. BT_LE_ADV_CONN is absent from the installed Zephyr 4.4 headers. The code now uses BT_LE_ADV_PARAM_INIT with BT_LE_ADV_OPT_CONN, GAP fast-interval-2 constants, and BT_LE_ADV_OPT_USE_IDENTITY. The identity option makes explicit the stable-address tradeoff already specified for Android companion association; it does not weaken GATT authentication, Secure Connections, or the physical pairing window.

Primary API reference:
https://github.com/zephyrproject-rtos/zephyr/blob/v4.4.0/include/zephyr/bluetooth/bluetooth.h

The compiler also warns that bt_passkey_set is deprecated. That supported API is retained for the fixed private passkey in this pinned prototype; the warning is not suppressed. Other compiler warnings remain visible in the build log. These changes require a new successful compile and real Bluetooth acceptance tests before daily use.
