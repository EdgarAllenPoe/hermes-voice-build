package org.tomstout.hermesvoice;
/** Per-message rejection must not prevent delivery of later messages. */
final class UploadPolicy {
    enum Action { ACCEPT,HOLD,RETRY,CONFIGURATION }
    static Action action(int status) {
        if(status==202)return Action.ACCEPT;
        if(status==400||status==409||status==413||status==415)return Action.HOLD;
        if(status==401||status==403||status==404||status==411)return Action.CONFIGURATION;
        return Action.RETRY;
    }
    static long delayMillis(int attempts) {
        return Math.min(60*60*1000L,30000L*(1L<<Math.min(7,Math.max(0,attempts-1))));
    }
}
