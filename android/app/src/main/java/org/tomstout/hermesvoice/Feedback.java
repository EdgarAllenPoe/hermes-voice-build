package org.tomstout.hermesvoice;
import android.content.Context;
import android.os.*;
final class Feedback {
    static void received(Context c){buzz(c,new long[]{0,55});}
    static void accepted(Context c){buzz(c,new long[]{0,45,110,45});}
    private static void buzz(Context c,long[] p){try{c.getSystemService(VibratorManager.class).getDefaultVibrator().vibrate(VibrationEffect.createWaveform(p,-1));}catch(RuntimeException ignored){}}
}
