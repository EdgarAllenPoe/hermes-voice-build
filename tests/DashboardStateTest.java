package org.tomstout.hermesvoice;
public final class DashboardStateTest {
    static int cases;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);cases++;}
    static DashboardState.Input ready(){
        DashboardState.Input s=new DashboardState.Input();
        s.now=1_000_000;s.serverAt=s.now;s.lastContact=s.now;s.lastUpload=s.now;
        s.configured=s.permissions=s.bluetooth=s.paired=s.relayEnabled=s.relayRunning=s.connected=s.serverOk=true;
        s.recorderQueue=0;s.sent=1;return s;
    }
    public static void main(String[] args){
        DashboardState.Input s=ready();check(new DashboardState(s).overall.level()==DashboardState.Level.GOOD,"verified path is green");
        s=ready();s.connected=false;check(new DashboardState(s).overall.level()==DashboardState.Level.WAIT,"idle never claims a live recorder");
        check(new DashboardState(s).recorder.title().equals("Standing by"),"idle is understandable");
        s=ready();s.relayEnabled=false;check(new DashboardState(s).overall.title().contains("paused"),"pause visible");
        s=ready();s.relayRunning=false;check(new DashboardState(s).overall.level()==DashboardState.Level.ERROR,"requested relay is not proof of running");
        s=ready();s.permissions=false;check(new DashboardState(s).overall.level()!=DashboardState.Level.GOOD,"missing permission");
        s=ready();s.paired=false;check(new DashboardState(s).overall.level()!=DashboardState.Level.GOOD,"missing bond");
        s=ready();s.bluetooth=false;check(new DashboardState(s).overall.level()!=DashboardState.Level.GOOD,"Bluetooth off");
        s=ready();s.configured=false;check(new DashboardState(s).server.level()==DashboardState.Level.WAIT,"missing settings");
        s=ready();s.pending=2;check(new DashboardState(s).delivery.level()==DashboardState.Level.WAIT,"pending upload is yellow");
        s=ready();s.held=1;check(new DashboardState(s).overall.level()==DashboardState.Level.ERROR,"held audio red");
        s=ready();s.storageOk=false;check(new DashboardState(s).overall.level()==DashboardState.Level.ERROR,"storage error red");
        s=ready();s.serverOk=false;s.serverMessage="Access token rejected";check(new DashboardState(s).overall.level()==DashboardState.Level.ERROR,"failed check red");
        s=ready();s.serverAt=s.now-DashboardState.SERVER_FRESH_MS-1;check(new DashboardState(s).server.level()==DashboardState.Level.WAIT,"old check expires");
        s=ready();s.serverAt=0;check(new DashboardState(s).server.title().equals("Not checked yet"),"no check is unknown");
        s=ready();s.serverAt=s.now+1;check(new DashboardState(s).server.level()==DashboardState.Level.WAIT,"clock rollback expires check");
        s=ready();s.tailRequired=true;s.tailPresent=false;check(new DashboardState(s).server.title().equals("Turn on Tailscale"),"missing private transport");
        s=ready();s.checking=true;check(new DashboardState(s).server.title().equals("Checking connection"),"checking is explicit");
        s=ready();s.notifications=false;check(new DashboardState(s).overall.level()==DashboardState.Level.WAIT,"notifications warning");
        s=ready();s.recorderQueue=15;check(new DashboardState(s).recorder.level()==DashboardState.Level.WAIT,"full recorder pending transfer");
        s=ready();s.quarantined=1;check(new DashboardState(s).recorder.level()==DashboardState.Level.ERROR,"quarantined recording visible");
        s=ready();s.recorderErrors=1;check(new DashboardState(s).recorder.level()==DashboardState.Level.WAIT,"historical errors do not claim current failure");
        s=ready();s.connected=false;s.lastContact=0;check(new DashboardState(s).recorder.detail().contains("Waiting"),"never seen recorder");
        s=ready();s.recorderQueue=-1;check(new DashboardState(s).recorder.detail().contains("Reading"),"unknown queue not zero");
        check(DashboardState.ago(10000,10000).equals("just now"),"fresh time");
        check(DashboardState.ago(100000,40000).equals("1 minute ago"),"minute");
        check(DashboardState.ago(10000,20000).contains("clock"),"future clock");
        System.out.println("Passed "+cases+" dashboard health scenarios");
    }
}
