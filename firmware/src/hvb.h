#ifndef HVB_H
#define HVB_H
#include <stdint.h>
#include <stddef.h>
#include <zephyr/kernel.h>
#include <zephyr/sys/atomic.h>
#define HVB_HEADER 64u
#define HVB_SLOT_SIZE 0x80000u
#define HVB_SLOTS 15
#define HVB_MAX_BYTES (64u+3000u*164u)
#define HVB_VAD_THRESHOLD 300u
extern atomic_t hvb_recording;
int hvb_store_init(void);
int hvb_store_begin(uint8_t header[64]);
int hvb_store_append(int slot,const uint8_t frame[164]);
int hvb_store_finish(int slot,uint32_t flags);
void hvb_store_abort(int slot);
int hvb_store_oldest(uint8_t meta[20]);
int hvb_store_next(uint8_t meta[20],uint16_t skip);
void hvb_store_diagnostics(uint8_t out[6]);
void hvb_capture_diagnostics(uint8_t out[12]);
int hvb_store_read(int slot,uint32_t offset,void *data,size_t len);
int hvb_store_ack(int slot,const uint8_t id[16]);
int hvb_store_count(void);
void hvb_store_gc_step(void);
int hvb_ble_init(void);
void hvb_ble_pair_window(void);
void hvb_ble_forget_phone(void);
void hvb_ble_maintenance(void);
uint16_t hvb_battery_mv(void);
void hvb_led(unsigned red,unsigned green,unsigned blue);
#endif
