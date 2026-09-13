#include "button.h"
#include <string.h>
void hvb_button_init(struct hvb_button *b) {
    memset(b,0,sizeof(*b));b->last_down=-1000;
}
enum hvb_button_action hvb_button_edge(struct hvb_button *b,int64_t at,bool down,bool recording) {
    if(!down){b->held=false;b->released=true;return HVB_BUTTON_NONE;}
    if(at-b->last_down<60)return HVB_BUTTON_NONE;
    b->last_down=at;b->held=true;b->held_since=at;b->pair_shown=b->forget_shown=false;
    if(recording){b->tentative=false;return HVB_BUTTON_STOP;}
    if(b->tentative&&b->released&&at-b->first<=500){
        b->tentative=false;return HVB_BUTTON_START;
    }
    b->first=at;b->released=false;b->tentative=true;
    return HVB_BUTTON_WAKE;
}
enum hvb_button_action hvb_button_poll(struct hvb_button *b,int64_t now,bool recording) {
    if(recording)return HVB_BUTTON_NONE;
    if(b->held&&now-b->held_since>10000&&!b->forget_shown){
        b->forget_shown=true;b->tentative=false;return HVB_BUTTON_FORGET;
    }
    if(b->held&&now-b->held_since>1500&&!b->pair_shown){
        b->pair_shown=true;b->tentative=false;return HVB_BUTTON_PAIR;
    }
    if(b->tentative&&now-b->first>500){
        b->tentative=false;return HVB_BUTTON_CANCEL;
    }
    return HVB_BUTTON_NONE;
}
