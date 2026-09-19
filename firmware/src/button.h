#ifndef HVB_BUTTON_H
#define HVB_BUTTON_H
#include <stdint.h>
#include <stdbool.h>
#define HVB_BUTTON_DEBOUNCE_MS 60
#define HVB_BUTTON_PAIR_MS 1500
#define HVB_BUTTON_FORGET_MS 10000
enum hvb_button_action {HVB_BUTTON_NONE,HVB_BUTTON_WAKE,HVB_BUTTON_START,HVB_BUTTON_STOP,
                        HVB_BUTTON_PAIR,HVB_BUTTON_FORGET};
struct hvb_button {
    int64_t held_since,last_up;
    bool held,release_pending,tentative,hold_allowed,pair_shown,forget_shown;
};
void hvb_button_init(struct hvb_button *b);
/* Feed edges and poll regularly. A tap starts only after a stable release;
 * microphone warmup starts on down. Holds never commit a recording. */
enum hvb_button_action hvb_button_edge(struct hvb_button *b,int64_t at,bool down,bool recording);
enum hvb_button_action hvb_button_poll(struct hvb_button *b,int64_t now,bool recording);
#endif
