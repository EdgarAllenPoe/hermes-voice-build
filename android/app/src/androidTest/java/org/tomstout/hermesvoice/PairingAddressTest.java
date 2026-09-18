package org.tomstout.hermesvoice;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.net.MacAddress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class PairingAddressTest {
    private Context context;
    @Before public void before(){
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Use the isolated CI package",target.getPackageName().endsWith(".ci"));
        context=new ContextWrapper(target){
            @Override public SharedPreferences getSharedPreferences(String name,int mode){
                return super.getSharedPreferences("pairing-address-regression",mode);
            }
        };
        Settings.prefs(context).edit().clear().commit();
    }
    @After public void after(){
        if(context!=null)Settings.prefs(context).edit().clear().commit();
    }
    @Test public void companionMacAddressBecomesValidBluetoothAddress(){
        // Exercise the real Android classes used on both sides of the failed handoff.
        String companion=MacAddress.fromString("C2:AB:34:CD:56:EF").toString();
        assertFalse("Reproduce the original rejection",BluetoothAdapter.checkBluetoothAddress(companion));
        assertTrue(BluetoothAdapter.checkBluetoothAddress(Settings.bluetoothAddress(companion)));
        assertEquals(MacAddress.fromString(companion),MacAddress.fromString(Settings.bluetoothAddress(companion)));
    }
    @Test public void validUppercaseAddressIsUnchanged(){
        assertEquals("C2:AB:34:CD:56:EF",Settings.bluetoothAddress("C2:AB:34:CD:56:EF"));
    }
    @Test public void malformedAndMissingAddressesAreRejected(){
        for(String value:new String[]{null,"","Hermes Voice","C2:AB:34:CD:56","C2:AB:34:CD:56:GG"}){
            try{Settings.bluetoothAddress(value);fail("Accepted a malformed address");}
            catch(IllegalArgumentException expected){}
        }
    }
    @Test public void legacySavedAddressIsRepairedWithoutChangingOtherSettings(){
        Settings.prefs(context).edit().putString("address","c2:ab:34:cd:56:ef")
            .putInt("association",123).putString("token","synthetic-retained-token").putBoolean("enabled",true).commit();
        assertEquals("C2:AB:34:CD:56:EF",Settings.recorderAddress(context));
        assertEquals("C2:AB:34:CD:56:EF",Settings.prefs(context).getString("address",null));
        assertEquals(123,Settings.prefs(context).getInt("association",-1));
        assertEquals("synthetic-retained-token",Settings.prefs(context).getString("token",null));
        assertTrue(Settings.enabled(context));
    }
    @Test public void unpairedInstallationStaysUnpaired(){
        assertNull(Settings.recorderAddress(context));
        assertFalse(Settings.prefs(context).contains("address"));
    }
}
