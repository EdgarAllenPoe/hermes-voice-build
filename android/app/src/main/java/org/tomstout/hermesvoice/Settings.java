package org.tomstout.hermesvoice;
import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
/** Secrets remain usable after the first post-boot unlock, including while locked. */
final class Settings {
    static final String DEFAULT_ENDPOINT="http://100.99.200.55:8765/v1/voice";
    static String endpoint(Context c){return prefs(c).getString("endpoint",DEFAULT_ENDPOINT);}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("settings",Context.MODE_PRIVATE);}
    // MacAddress.toString() is lowercase; Android Bluetooth APIs require uppercase.
    static String bluetoothAddress(String address){
        if(address==null)throw new IllegalArgumentException("Recorder has no Bluetooth address; pair again");
        String normalized=address.toUpperCase(java.util.Locale.ROOT);
        if(!android.bluetooth.BluetoothAdapter.checkBluetoothAddress(normalized))
            throw new IllegalArgumentException("Recorder address is invalid; pair again");
        return normalized;
    }
    static String recorderAddress(Context c){
        SharedPreferences p=prefs(c);
        String saved=p.getString("address",null);
        if(saved==null)return null;
        String normalized=bluetoothAddress(saved);
        // Repair addresses saved by 0.3.0 without replacing bonds, credentials or audio.
        if(!normalized.equals(saved))p.edit().putString("address",normalized).apply();
        return normalized;
    }
    static void metric(Context c,String key,String value){
        c.getSharedPreferences("diagnostics",Context.MODE_PRIVATE).edit().putString(key,value).apply();
    }
    static void recorderSnapshot(Context c,String text){
        c.getSharedPreferences("diagnostics",Context.MODE_PRIVATE).edit()
            .putString("recorder",text).putLong("recorder_read_at",System.currentTimeMillis()).apply();
    }
    static void recorderUpdate(Context c,RecorderStatus status,boolean fullRead){
        SharedPreferences.Editor edit=c.getSharedPreferences("diagnostics",Context.MODE_PRIVATE).edit()
            .putString("recorder",status.text()).putInt("recorder_queue",status.queued())
            .putBoolean("recorder_queue_estimated",status.estimated())
            .putLong("recorder_contact_at",System.currentTimeMillis());
        if(fullRead)edit.putLong("recorder_read_at",System.currentTimeMillis());
        if(status.info()!=null)edit.putInt("recorder_quarantined",status.info().quarantined)
            .putLong("recorder_errors",status.info().errors);
        if(status.info()!=null&&status.info().enhanced){RecorderInfo i=status.info();edit.putBoolean("enhanced",true).putString("battery",i.batteryText()).putBoolean("low_battery",i.lowBattery)
            .putInt("charge_state",i.chargeState).putInt("mic_state",i.micState).putInt("mic_level",i.level).putInt("mic_peak",i.peak).putLong("mic_frames",i.micFrames)
            .putInt("silence_ms",i.silenceMs).putInt("threshold",i.threshold).putBoolean("manual",i.manual).putInt("free_slots",i.freeSlots);}
        else edit.putBoolean("enhanced",false);
        edit.apply();
    }
    static synchronized void saveServer(Context c,String address,String secret)throws Exception{
        Endpoint.parse(address);
        SharedPreferences p=prefs(c);SharedPreferences.Editor edit=p.edit();
        if(secret.isEmpty())token(c);
        else {
            if(secret.length()<32)throw new IllegalArgumentException("Token must contain at least 32 characters");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
            edit.putString("token",Base64.encodeToString(cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP))
                .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP));
        }
        if(!edit.putString("endpoint",address.trim()).putLong("server_revision",p.getLong("server_revision",0)+1).commit())
            throw new java.io.IOException("Settings could not be saved");
    }
    static synchronized void serverHealth(Context c,long revision,boolean ok,String message){
        if(revision!=prefs(c).getLong("server_revision",0))return;
        c.getSharedPreferences("diagnostics",Context.MODE_PRIVATE).edit()
            .putLong("server_revision",revision).putBoolean("server_ok",ok)
            .putLong("server_checked_at",System.currentTimeMillis()).putString("server_message",message).apply();
    }
    static String diagnostics(Context c){
        SharedPreferences d=c.getSharedPreferences("diagnostics",Context.MODE_PRIVATE);
        StringBuilder out=new StringBuilder("Hermes Voice "+BuildConfig.VERSION_NAME+"\n");
        for(String key:new String[]{"connection","transfer","mtu","recorder","recorder_problem","last_upload","battery","device_action","processing_status"}){
            String value=d.getString(key,"not available");
            if(key.equals("recorder")&&!value.equals("not available")){
                long readAt=d.getLong("recorder_read_at",0);
                value+="\nRecorder details last read: "+
                    (readAt==0?"unknown (older app snapshot)":new java.util.Date(readAt).toString());
                value+="\nRecorder status: last known; updated when connected";
            }
            if(key.equals("last_upload")&&!value.equals("not available")){
                try{value=new java.util.Date(Long.parseLong(value)).toString();}catch(NumberFormatException ignored){}
            }
            out.append(key.replace('_',' ')).append(": ").append(value).append("\n");
        }
        if(d.getLong("server_revision",-1)==prefs(c).getLong("server_revision",0)){
            long checked=d.getLong("server_checked_at",0);
            out.append("Server check: ").append(d.getString("server_message","not checked")).append("\n");
            if(checked>0)out.append("Server checked at: ").append(new java.util.Date(checked)).append("\n");
        }
        return out.toString();
    }
    static boolean enabled(Context c){return prefs(c).getBoolean("enabled",false);}
    static void status(Context c,String s){prefs(c).edit().putString("status",s).apply();}
    static SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias("hermes_voice_token")) {
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder("hermes_voice_token",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setUserAuthenticationRequired(false).build());g.generateKey();
        }
        return (SecretKey)ks.getKey("hermes_voice_token",null);
    }
    static void token(Context c,String s)throws Exception {
        if(s.length()<32)throw new IllegalArgumentException("Token must contain at least 32 characters");
        Cipher x=Cipher.getInstance("AES/GCM/NoPadding");x.init(Cipher.ENCRYPT_MODE,key());
        prefs(c).edit().putString("token",Base64.encodeToString(x.doFinal(s.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP))
            .putString("iv",Base64.encodeToString(x.getIV(),Base64.NO_WRAP)).commit();
    }
    static String token(Context c)throws Exception {
        SharedPreferences p=prefs(c);if(!p.contains("token"))throw new IllegalStateException("Server token is not configured");
        Cipher x=Cipher.getInstance("AES/GCM/NoPadding");x.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p.getString("iv",""),Base64.NO_WRAP)));
        return new String(x.doFinal(Base64.decode(p.getString("token",""),Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
}
