package org.tomstout.hermesvoice;

/** A recorder snapshot adjusted only by confirmed recorder ACKs, never by uploads. */
final class RecorderStatus {
    private RecorderInfo snapshot;
    private int queued;
    private boolean estimated;

    void update(RecorderInfo info) {
        snapshot=info;queued=info.queued;estimated=false;
    }
    void acknowledged() {
        if(snapshot!=null){queued=Math.max(0,queued-1);estimated=true;}
    }
    int queued(){return snapshot==null?-1:queued;}
    RecorderInfo info(){return snapshot;}
    boolean estimated(){return estimated;}
    String text() {
        if(snapshot==null)return "not available";
        // The remaining details still describe the last INFO read.
        String details=snapshot.text.substring(snapshot.text.indexOf('\n'));
        return "Recorder queue: "+queued+
               (estimated?" (estimated after confirmed transfer)":"")+details;
    }
}
