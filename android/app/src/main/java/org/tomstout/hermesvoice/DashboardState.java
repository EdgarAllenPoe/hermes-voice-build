package org.tomstout.hermesvoice;

/** User-facing health rules, independent of Android and of presentation colors. */
final class DashboardState {
    enum Level { GOOD, WAIT, ERROR }
    record Status(Level level,String title,String detail) {}
    static final long SERVER_FRESH_MS=120_000;
    static final class Input {
        long now,serverAt,lastContact,lastUpload;
        boolean configured,permissions,bluetooth,paired,relayEnabled,relayRunning,connected;
        boolean notifications=true,storageOk=true,tailRequired,tailPresent,serverOk,checking;
        int pending,held,sent,recorderQueue=-1,quarantined;
        long recorderErrors;
        String serverMessage="";
    }
    final Status overall,recorder,delivery,server;
    DashboardState(Input s) {
        if(!s.permissions)recorder=waitFor("Permission needed","Allow Nearby devices in Diagnostics to connect your recorder.");
        else if(!s.bluetooth)recorder=waitFor("Bluetooth is off","Turn on Bluetooth on this phone.");
        else if(!s.paired)recorder=waitFor("Not paired yet","Pair your voice recorder in Diagnostics.");
        else if(s.quarantined>0)recorder=error("Recording needs attention",s.quarantined+" recorder slot(s) need review. Open Diagnostics.");
        else if(s.connected) {
            String queue=s.recorderQueue<0?"Reading recorder status\u2026":"Last known queue: "+s.recorderQueue;
            recorder=s.recorderQueue<0?waitFor("Reading status",queue):s.recorderErrors>0?waitFor("Check recorder",queue+". Errors were reported since its last restart."):
                s.recorderQueue>0?waitFor("Transferring recordings",queue+". Keep the recorder nearby."):
                good("Connected",queue+". Your recorder is within reach.");
        } else recorder=waitFor("Standing by",s.lastContact>0?
            "Last contact "+ago(s.now,s.lastContact)+". Keep the recorder nearby; reconnection is automatic while the relay runs.":
            "Waiting for your recorder. Make a recording or hold its button for two seconds.");

        if(!s.storageOk)delivery=error("Phone storage problem","Open Diagnostics. Existing recordings have not been cleared.");
        else if(s.held>0)delivery=error(s.held+" held for review","These recordings remain on your phone. Review them in Diagnostics.");
        else if(s.pending>0)delivery=waitFor(s.pending+" waiting to send","Saved on this phone. Delivery retries while the relay is enabled.");
        else delivery=good(s.sent>0?"All phone recordings sent":"Nothing waiting",s.lastUpload>0?
            "Last server receipt "+ago(s.now,s.lastUpload)+".":"Your phone has no recordings waiting to upload.");

        if(!s.configured)server=waitFor("Setup needed","Add your server address and token in Diagnostics.");
        else if(s.tailRequired&&!s.tailPresent)server=waitFor("Turn on Tailscale","Open Tailscale on this phone, then check the connection.");
        else if(s.checking)server=waitFor("Checking connection","Verifying the server and your access token\u2026");
        else if(s.serverAt<=0)server=waitFor("Not checked yet","Tap Check connection to verify the server and token.");
        else if(!s.serverOk)server=error("Connection needs attention",s.serverMessage.isEmpty()?
            "Check Tailscale and server settings, then try again.":s.serverMessage);
        else if(s.now<s.serverAt||s.now-s.serverAt>SERVER_FRESH_MS)server=waitFor("Check is out of date","Last verified "+ago(s.now,s.serverAt)+". Tap Check connection.");
        else server=good("Verified","Server and token verified "+ago(s.now,s.serverAt)+".");

        if(!s.configured||!s.paired||!s.permissions)overall=waitFor("Let\u2019s get you connected","Open Diagnostics to finish setup. Your existing recordings stay saved.");
        else if(!s.relayEnabled)overall=waitFor("Relay is paused","Resume the relay to receive and deliver recordings.");
        else if(!s.relayRunning)overall=error("Relay needs a restart","Tap Start relay to restore the phone connection.");
        else if(!s.bluetooth)overall=waitFor("Turn on Bluetooth","The phone needs Bluetooth to receive your recordings.");
        else if(delivery.level==Level.ERROR)overall=error("Delivery needs attention",delivery.detail);
        else if(recorder.level==Level.ERROR)overall=error("Recorder needs attention",recorder.detail);
        else if(server.level==Level.ERROR)overall=error("Check your server",server.detail);
        else if(!s.notifications)overall=waitFor("Notifications are off","Enable notifications in Diagnostics so the relay can keep you informed.");
        else if(server.level!=Level.GOOD)overall=waitFor("Check your connection",server.detail);
        else if(s.pending>0||s.recorderQueue>0&&s.connected)overall=waitFor("Your recordings are on their way","Keep the relay running while delivery finishes.");
        else if(recorder.level!=Level.GOOD)overall=waitFor("Standing by",s.recorderErrors>0&&s.connected?
            recorder.detail:"The phone is ready. Recorder presence will be confirmed when it reconnects.");
        else overall=good("Ready to record","Your recorder is connected and the server is reachable.");
    }
    static Status good(String title,String detail){return new Status(Level.GOOD,title,detail);}
    static Status waitFor(String title,String detail){return new Status(Level.WAIT,title,detail);}
    static Status error(String title,String detail){return new Status(Level.ERROR,title,detail);}
    static String ago(long now,long at){
        if(at<=0)return "never";
        if(now<at)return "at an earlier clock setting";
        long seconds=(now-at)/1000;
        if(seconds<10)return "just now";
        if(seconds<60)return seconds+" seconds ago";
        long minutes=seconds/60;
        if(minutes<60)return minutes+(minutes==1?" minute ago":" minutes ago");
        long hours=minutes/60;
        if(hours<24)return hours+(hours==1?" hour ago":" hours ago");
        long days=hours/24;return days+(days==1?" day ago":" days ago");
    }
}
