package org.tomstout.hermesvoice;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){if(i==null||!(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())))return;RetentionJob.schedule(c);if(Settings.enabled(c)){UploadJob.schedule(c);MainActivity.observe(c);}}
}
