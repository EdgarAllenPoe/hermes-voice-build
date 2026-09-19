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
    static String diagnostics(Context c){
        SharedPreferences d=c.getSharedPreferences("diagnostics",Context.MODE_PRIVATE);
        StringBuilder out=new StringBuilder("Hermes Voice "+BuildConfig.VERSION_NAME+"\n");
        for(String key:new String[]{"connection","transfer","mtu","recorder","recorder_problem","last_upload"}){
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
