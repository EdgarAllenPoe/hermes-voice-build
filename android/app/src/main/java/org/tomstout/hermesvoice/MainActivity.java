package org.tomstout.hermesvoice;
import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.companion.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;
public final class MainActivity extends Activity {
    private EditText url,token;private TextView state;
    @Override public void onCreate(Bundle b){super.onCreate(b);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(28,42,28,32);
        ScrollView scroll=new ScrollView(this);scroll.addView(box);setContentView(scroll);
        TextView title=new TextView(this);title.setText("Hermes Voice setup");title.setTextSize(25);box.addView(title);
        TextView explanation=new TextView(this);explanation.setText("The small recorder uses its own microphone. Keep Bluetooth and Tailscale enabled. First unlock after reboot is required. Your digital assistant is unchanged.");box.addView(explanation);
        url=new EditText(this);url.setHint(Settings.DEFAULT_ENDPOINT);url.setSingleLine();url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);url.setText(Settings.endpoint(this));box.addView(url);
        token=new EditText(this);token.setHint("Server token (leave blank to keep stored token)");token.setSingleLine();token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);box.addView(token);
        button(box,"Save server settings",()->{try{Endpoint.parse(url.getText().toString());if(!token.getText().toString().isEmpty())Settings.token(this,token.getText().toString().trim());else Settings.token(this);Settings.prefs(this).edit().putString("endpoint",url.getText().toString().trim()).commit();token.setText("");toast("Settings saved");}catch(Exception e){toast(e.getMessage());}});
        button(box,"Pair voice button",this::pair);
        button(box,"Start relay",()->{if(!permissions()){request();return;}try{Endpoint.parse(Settings.endpoint(this));Settings.token(this);if(Settings.prefs(this).getInt("association",-1)<0)throw new IllegalStateException("Pair the recorder first");Settings.prefs(this).edit().putBoolean("enabled",true).commit();observe(this);RelayService.start(this);UploadJob.schedule(this);refresh();}catch(Exception e){toast(e.getMessage());}});
        button(box,"Stop relay",()->{Settings.prefs(this).edit().putBoolean("enabled",false).commit();stopService(new Intent(this,RelayService.class));getSystemService(android.app.job.JobScheduler.class).cancel(9041);refresh();});
        button(box,"Retry queued uploads",()->{ExecutorService x=Executors.newSingleThreadExecutor();x.submit(()->{Uploader.drain(this);runOnUiThread(this::refresh);x.shutdown();});});
        button(box,"Refresh status",this::refresh);
        state=new TextView(this);state.setTextIsSelectable(true);box.addView(state);refresh();if(!permissions())request();
    }
    private void button(LinearLayout l,String text,Runnable r){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->r.run());l.addView(b);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private boolean permissions(){return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED;}
    private void request(){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.POST_NOTIFICATIONS},100);}
    private void refresh(){try(QueueDb d=new QueueDb(this)){state.setText("Relay: "+(Settings.enabled(this)?"enabled":"stopped")+"\nDevice: "+Settings.prefs(this).getString("address","not paired")+"\n"+d.summary()+"\n"+Settings.prefs(this).getString("status","Ready for setup"));}}
    private void pair(){if(!permissions()){request();return;}
        BluetoothAdapter a=getSystemService(BluetoothManager.class).getAdapter();if(a==null||!a.isEnabled()){toast("Enable Bluetooth first");return;}
        toast("Hold the recorder button for 1.5 seconds, then release. Enter its local pairing code when Android asks.");
        ScanFilter scan=new ScanFilter.Builder().setServiceUuid(new ParcelUuid(Wire.SERVICE)).build();
        BluetoothLeDeviceFilter filter=new BluetoothLeDeviceFilter.Builder().setScanFilter(scan).build();
        AssociationRequest request=new AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(true).build();
        try{getSystemService(CompanionDeviceManager.class).associate(request,getMainExecutor(),new CompanionDeviceManager.Callback(){
            @Override public void onAssociationPending(IntentSender sender){try{startIntentSenderForResult(sender,101,null,0,0,0);}catch(Exception e){toast(e.getMessage());}}
            @Override public void onAssociationCreated(AssociationInfo info){
                if(info.getDeviceMacAddress()==null){toast("Association has no Bluetooth address");return;}
                String address=info.getDeviceMacAddress().toString();Settings.prefs(MainActivity.this).edit().putInt("association",info.getId()).putString("address",address).commit();
                try{BluetoothDevice dev=a.getRemoteDevice(address);if(dev.getBondState()!=BluetoothDevice.BOND_BONDED)dev.createBond();observe(MainActivity.this);toast("Association saved. Complete Bluetooth pairing, then Start relay.");refresh();}catch(Exception e){toast(e.getMessage());}
            }
            @Override public void onFailure(CharSequence error){toast("Pairing: "+error);}
        });}catch(Exception e){toast(e.getMessage());}
    }
    static void observe(Context c){
        int id=Settings.prefs(c).getInt("association",-1);String address=Settings.prefs(c).getString("address",null);if(id<0||address==null)return;
        try{CompanionDeviceManager m=c.getSystemService(CompanionDeviceManager.class);
            if(Build.VERSION.SDK_INT>=36)m.startObservingDevicePresence(new ObservingDevicePresenceRequest.Builder().setAssociationId(id).build());
            else m.startObservingDevicePresence(address);
        }catch(Exception e){Settings.status(c,"Companion observation needs attention: "+e.getMessage());}
    }
}
