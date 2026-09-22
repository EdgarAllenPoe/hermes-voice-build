#ifndef HVB_RECORDER_CONFIG_H
#define HVB_RECORDER_CONFIG_H
#include <stdint.h>
struct hvb_config { uint16_t silence_ms, threshold; uint8_t manual; };
void hvb_config_get(struct hvb_config *out);
int hvb_config_set(const uint8_t bytes[6]);
void hvb_config_bytes(uint8_t out[6]);
#endif
