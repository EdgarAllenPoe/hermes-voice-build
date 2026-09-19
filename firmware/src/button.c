#include "button.h"
void hvb_button_init(struct hvb_button *b) {
    b->held_since=b->last_up=0;
    b->held=b->release_pending=b->tentative=b->hold_allowed=false;
    b->pair_shown=b->forget_shown=false;
}
enum hvb_button_action hvb_button_edge(struct hvb_button *b,int64_t at,bool down,bool recording) {
    if(!down){
        if(b->held){b->held=false;b->last_up=at;b->release_pending=true;}
        return HVB_BUTTON_NONE;
    }
    if(b->held)return HVB_BUTTON_NONE;
    b->held=true;
    if(b->release_pending){
        /* Contact bounced closed before poll confirmed a stable release. */
        b->release_pending=false;
        return HVB_BUTTON_NONE;
    }
    b->held_since=at;b->pair_shown=b->forget_shown=false;
    b->hold_allowed=b->tentative=!recording;
    return recording?HVB_BUTTON_STOP:HVB_BUTTON_WAKE;
}
static enum hvb_button_action hold_action(struct hvb_button *b,int64_t duration) {
    if(!b->hold_allowed)return HVB_BUTTON_NONE;
    if(duration>=HVB_BUTTON_FORGET_MS&&!b->forget_shown){
        b->forget_shown=b->pair_shown=true;b->tentative=false;
        return HVB_BUTTON_FORGET;
    }
    if(duration>=HVB_BUTTON_PAIR_MS&&!b->pair_shown){
        b->pair_shown=true;b->tentative=false;
        return HVB_BUTTON_PAIR;
    }
    return HVB_BUTTON_NONE;
}
enum hvb_button_action hvb_button_poll(struct hvb_button *b,int64_t now,bool recording) {
    if(b->release_pending&&now-b->last_up>=HVB_BUTTON_DEBOUNCE_MS){
        b->release_pending=false;
        /* Also classify holds when the audio loop was delayed at release. */
        enum hvb_button_action action=recording?HVB_BUTTON_NONE:hold_action(b,b->last_up-b->held_since);
        b->hold_allowed=false;
        if(action!=HVB_BUTTON_NONE)return action;
        if(b->tentative&&!recording){b->tentative=false;return HVB_BUTTON_START;}
        b->tentative=false;
    }
    if(recording||!b->held)return HVB_BUTTON_NONE;
    return hold_action(b,now-b->held_since);
}
