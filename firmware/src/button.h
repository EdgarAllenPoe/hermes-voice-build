#ifndef HVB_BUTTON_H
#define HVB_BUTTON_H
#include <stdint.h>
#include <stdbool.h>
enum hvb_button_action {HVB_BUTTON_NONE,HVB_BUTTON_WAKE,HVB_BUTTON_START,HVB_BUTTON_STOP,
                        HVB_BUTTON_CANCEL,HVB_BUTTON_PAIR,HVB_BUTTON_FORGET};
struct hvb_button {
    int64_t first,last_down,held_since;
    bool held,released,tentative,pair_shown,forget_shown;
};
void hvb_button_init(struct hvb_button *b);
enum hvb_button_action hvb_button_edge(struct hvb_button *b,int64_t at,bool down,bool recording);
enum hvb_button_action hvb_button_poll(struct hvb_button *b,int64_t now,bool recording);
#endif
