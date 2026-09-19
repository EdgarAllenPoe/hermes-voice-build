package org.tomstout.hermesvoice;

import android.Manifest;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

/** Read current prerequisites and typed telemetry without inferring health from status prose. */
final class DashboardReader {
    record Snapshot(DashboardState.Input input,String diagnostics) {}
    static Snapshot read(Context c,boolean checking) {
        DashboardState.Input s=new DashboardState.Input();s.now=System.currentTimeMillis();s.checking=checking;
        SharedPreferences p=Settings.prefs(c),d=c.getSharedPreferences("diagnostics",Context.MODE_PRIVATE);
        s.relayEnabled=Settings.enabled(c);s.relayRunning=RelayService.isRunning();s.connected=RelayService.isConnected();
        s.permissions=c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED&&
            c.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED;
        s.notifications=c.getSystemService(NotificationManager.class).areNotificationsEnabled();
        try {
            BluetoothAdapter adapter=c.getSystemService(BluetoothManager.class).getAdapter();
            s.bluetooth=s.permissions&&adapter!=null&&adapter.isEnabled();
            String address=Settings.recorderAddress(c);
            s.paired=s.permissions&&address!=null&&p.getInt("association",-1)>=0&&adapter!=null&&
                adapter.getRemoteDevice(address).getBondState()==android.bluetooth.BluetoothDevice.BOND_BONDED;
        }catch(Exception ignored){}
        try {
            s.tailRequired=Endpoint.parse(Settings.endpoint(c)).getProtocol().equals("http");
            s.tailPresent=Endpoint.tailInterfacePresent();
            Settings.token(c);s.configured=true;
        }catch(Exception ignored){}
        if(d.getLong("server_revision",-1)==p.getLong("server_revision",0)){
            s.serverAt=d.getLong("server_checked_at",0);s.serverOk=d.getBoolean("server_ok",false);
            s.serverMessage=d.getString("server_message","");
        }
        s.lastContact=d.getLong("recorder_contact_at",0);
        try{s.lastUpload=Long.parseLong(d.getString("last_upload","0"));}catch(NumberFormatException ignored){}
        s.recorderQueue=d.getInt("recorder_queue",-1);
        s.quarantined=d.getInt("recorder_quarantined",0);
        s.recorderErrors=d.getLong("recorder_errors",0);
        String queue;
        try(QueueDb db=new QueueDb(c)){
            QueueDb.Stats stats=db.stats();s.pending=stats.pending();s.held=stats.held();s.sent=stats.sent();queue=db.summary();
        }catch(Exception ignored){s.storageOk=false;queue="Phone storage could not be read.";}
        String text="Relay requested: "+(s.relayEnabled?"enabled":"paused")+
            "\nRelay running: "+s.relayRunning+"\nBluetooth permission: "+s.permissions+
            "\nBluetooth enabled: "+s.bluetooth+"\nRecorder bonded: "+s.paired+
            "\nNotifications enabled: "+s.notifications+"\n\n"+queue+"\n\n"+
            Settings.diagnostics(c)+"\n"+p.getString("status","Ready for setup");
        return new Snapshot(s,text);
    }
}
