#ifndef HVB_CHARGE_INDICATOR_H
#define HVB_CHARGE_INDICATOR_H
#include <stdbool.h>
#include <stdint.h>
/* Pure indication policy; it never changes charging limits or clears faults. */
bool hvb_charge_indicator(bool valid, uint8_t vbus, int32_t voltage_mv,
                          int64_t current_ua, uint8_t status, uint8_t error,
                          uint32_t charge_current_ua);
#endif
