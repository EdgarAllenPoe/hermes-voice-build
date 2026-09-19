#include "charge_indicator.h"

bool hvb_charge_indicator(bool valid, uint8_t vbus, int32_t voltage_mv,
                          int64_t current_ua, uint8_t status, uint8_t error,
                          uint32_t charge_current_ua)
{
    /* Follow Seeed's XIAO example: USB + plausible VBAT + positive IBAT.
     * Do not use the D00 COMPLETE bit or interpret LED-off as proof of full.
     * The 10% threshold matches the configured trickle/termination current.
     * Reject VBUS over/undervoltage, suspension, thermal pause and faults. */
    return valid && (vbus & 0x01U) && !(vbus & 0x1CU) &&
           voltage_mv >= 2000 && !error && !(status & 0x40U) &&
           charge_current_ua >= 10 &&
           current_ua >= (int64_t)(charge_current_ua / 10U);
}
