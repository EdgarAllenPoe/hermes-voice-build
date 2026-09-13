package org.tomstout.hermesvoice;
import android.companion.*;
import android.content.*;
import android.os.Build;
public final class CompanionService extends CompanionDeviceService {
    private void start(int id){if(id==Settings.prefs(this).getInt("association",-1))RelayService.start(this);}
    @Override public void onDeviceAppeared(AssociationInfo a){start(a.getId());}
    @Override public void onDevicePresenceEvent(DevicePresenceEvent e){
        if(Build.VERSION.SDK_INT<36)return;
        if(e.getEvent()==DevicePresenceEvent.EVENT_BLE_APPEARED||e.getEvent()==DevicePresenceEvent.EVENT_BT_CONNECTED)start(e.getAssociationId());
    }
}
