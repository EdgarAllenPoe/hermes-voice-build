#ifndef HVB_CODEC_H
#define HVB_CODEC_H
#include <stdint.h>
#define HVB_FRAME_SAMPLES 320
#define HVB_FRAME_BYTES 164
/* Mean absolute deviation in 16-bit PCM units, independent of DC offset. */
/* Sense board: quiet room 3-8, test speech up to 139 (2026-09-18). */
#define HVB_VAD_THRESHOLD 40u
void hvb_encode_frame(const int16_t pcm[HVB_FRAME_SAMPLES], uint8_t out[HVB_FRAME_BYTES]);
void hvb_decode_frame(const uint8_t in[HVB_FRAME_BYTES], int16_t pcm[HVB_FRAME_SAMPLES]);
struct hvb_gate { unsigned frames, voiced_run, silence; int heard; };
/* 0=continue, 1=end after silence, 2=no speech, 3=limit reached. */
int hvb_gate_update(struct hvb_gate *g, const int16_t *pcm, unsigned threshold);
#endif
