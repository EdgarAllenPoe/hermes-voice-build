package org.tomstout.hermesvoice;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){if(Settings.enabled(c)){UploadJob.schedule(c);MainActivity.observe(c);}}
}
