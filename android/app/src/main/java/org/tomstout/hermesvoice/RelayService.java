package org.tomstout.hermesvoice;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.*;

/** GATT callbacks, spool I/O and durable receipts share one worker thread. */
public final class RelayService extends Service {
    private HandlerThread thread;
    private Handler handler;
    private final ExecutorService upload=Executors.newSingleThreadExecutor();
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic ctrl,meta,data,info;
    private volatile boolean destroyed;
    private long deadline,lastProgress;
    private TransferEngine engine;
    private final RecorderStatus recorderStatus=new RecorderStatus();
    static void start(Context c){
        if(!Settings.enabled(c))return;
        try{c.startForegroundService(new Intent(c,RelayService.class));}
        catch(RuntimeException e){Settings.status(c,"Open Hermes Voice and tap Start relay");}
    }
    @Override public void onCreate(){
        super.onCreate();
        NotificationManager n=getSystemService(NotificationManager.class);
        n.createNotificationChannel(new NotificationChannel("relay","Voice recorder connection",NotificationManager.IMPORTANCE_LOW));
        PendingIntent p=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,"relay").setSmallIcon(R.drawable.ic_voice)
            .setContentTitle(BuildConfig.CI_BUILD?"Hermes Voice CI test relay":"Hermes Voice relay")
            .setContentText("Ready for your voice button").setContentIntent(p).setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build();
        startForeground(12,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        thread=new HandlerThread("HermesRecorderIO");thread.start();handler=new Handler(thread.getLooper());
        engine=new TransferEngine(new File(getFilesDir(),"incoming"),bytes->{
            try(QueueDb db=new QueueDb(this)){return db.accept(bytes);}
        },new TransferEngine.Port(){
            public void write(byte[] bytes){RelayService.this.write(bytes);}
            public void read(boolean metadata){RelayService.this.read(metadata?meta:data);}
            public void idle(){
                Settings.metric(RelayService.this,"transfer","Idle");
                handler.postDelayed(next,2000);
            }
            public void progress(int received,int total){
                long now=SystemClock.elapsedRealtime();
                if(received==total||received==0||now-lastProgress>=500){
                    Settings.metric(RelayService.this,"transfer",received+" / "+total+" bytes");
                    lastProgress=now;
                }
            }
            public void saved(boolean fresh){
                if(fresh)Feedback.received(RelayService.this);
                Settings.status(RelayService.this,"Recording safely saved on phone");
                if(!destroyed){upload.submit(()->Uploader.drain(RelayService.this));UploadJob.schedule(RelayService.this);}
            }
            public void acknowledged(){
                recorderStatus.acknowledged();
                Settings.metric(RelayService.this,"recorder",recorderStatus.text());
                Settings.metric(RelayService.this,"transfer","Saved on phone; recorder confirmed receipt");
            }
            public void refreshInfo(){
                if(info!=null)RelayService.this.read(info);else engine.infoRead();
            }
            public void problem(String code){
                Settings.metric(RelayService.this,"recorder_problem",code);
                Settings.status(RelayService.this,"Corrupt recording retained; continuing with the next");
            }
        });
        handler.post(this::connect);handler.postDelayed(watchdog,2000);
        upload.submit(()->Uploader.drain(this));
    }
    @Override public int onStartCommand(Intent i,int flags,int startId){
        if(!Settings.enabled(this)){stopSelf();return START_NOT_STICKY;}return START_STICKY;
    }
    @Override public IBinder onBind(Intent i){return null;}
    private final Runnable watchdog=new Runnable(){public void run(){
        if(destroyed)return;
        if(deadline!=0&&SystemClock.elapsedRealtime()>deadline)fail("Bluetooth request timed out");
        handler.postDelayed(this,2000);
    }};
    private final Runnable next=()->{if(!destroyed&&gatt!=null)engine.next();};
    private void waiting(){deadline=SystemClock.elapsedRealtime()+25000;}
    private void connect(){
        if(destroyed||!Settings.enabled(this)||gatt!=null)return;
        try{
            String address=Settings.recorderAddress(this);
            BluetoothAdapter a=getSystemService(BluetoothManager.class).getAdapter();
            if(address==null||a==null||!a.isEnabled()){
                Settings.metric(this,"connection","Bluetooth off or recorder unpaired");
                handler.postDelayed(this::connect,10000);return;
            }
            gatt=a.getRemoteDevice(address).connectGatt(this,true,callback,BluetoothDevice.TRANSPORT_LE);
            Settings.metric(this,"connection","Waiting for recorder");
        }catch(SecurityException e){fail("Bluetooth permission was revoked");}catch(Exception e){fail("Bluetooth unavailable");}
    }
    private void closeConnection(){
        try{engine.disconnected();}catch(IOException e){Settings.metric(this,"recorder_problem","spool_sync_failed");}
        ctrl=meta=data=info=null;
        if(gatt!=null){try{gatt.disconnect();gatt.close();}catch(SecurityException ignored){}catch(RuntimeException ignored){}gatt=null;}
    }
    private void fail(String reason){
        if(destroyed)return;
        Settings.status(this,reason+"; recordings retained");Settings.metric(this,"connection","Disconnected");
        deadline=0;closeConnection();handler.removeCallbacks(next);handler.postDelayed(this::connect,5000);
    }
    private void write(byte[] value){
        if(destroyed||gatt==null||ctrl==null)return;
        waiting();
        try{if(gatt.writeCharacteristic(ctrl,value,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)!=BluetoothStatusCodes.SUCCESS)
            fail("Bluetooth write could not start");}
        catch(SecurityException e){fail("Bluetooth permission was revoked");}
        catch(RuntimeException e){fail("Bluetooth permission or write error");}
    }
    private void read(BluetoothGattCharacteristic characteristic){
        if(destroyed||gatt==null)return;
        waiting();
        try{if(!gatt.readCharacteristic(characteristic))fail("Bluetooth read could not start");}
        catch(SecurityException e){fail("Bluetooth permission was revoked");}
        catch(RuntimeException e){fail("Bluetooth permission or read error");}
    }
    private void startTransfer(boolean skip){
        try{engine.connected(skip);}catch(IOException e){fail("Phone spool could not open");}
    }
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state){handler.post(()->{
            if(g!=gatt||destroyed)return;
            if(status!=BluetoothGatt.GATT_SUCCESS||state==BluetoothProfile.STATE_DISCONNECTED){fail("Recorder disconnected");return;}
            if(state==BluetoothProfile.STATE_CONNECTED){
                Settings.metric(RelayService.this,"connection","Connected; discovering services");
                if(checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED){fail("Bluetooth permission was revoked");return;}
                waiting();if(!g.requestMtu(247)&&!g.discoverServices())fail("Cannot discover recorder");
            }
        });}
        @Override public void onMtuChanged(BluetoothGatt g,int mtu,int status){handler.post(()->{
            if(g!=gatt||destroyed)return;
            Settings.metric(RelayService.this,"mtu",Integer.toString(mtu));
            if(checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED){fail("Bluetooth permission was revoked");return;}
            waiting();if(!g.discoverServices())fail("Service discovery could not start");
        });}
        @Override public void onServicesDiscovered(BluetoothGatt g,int status){handler.post(()->{
            if(g!=gatt||destroyed)return;
            BluetoothGattService service=g.getService(Wire.SERVICE);
            if(status!=BluetoothGatt.GATT_SUCCESS||service==null){fail("Recorder service not found");return;}
            ctrl=service.getCharacteristic(Wire.CONTROL);meta=service.getCharacteristic(Wire.META);
            data=service.getCharacteristic(Wire.DATA);info=service.getCharacteristic(Wire.INFO);
            if(ctrl==null||meta==null||data==null){fail("Recorder protocol mismatch");return;}
            Settings.metric(RelayService.this,"connection","Connected");
            if(info!=null)read(info);else startTransfer(false);
        });}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){handler.post(()->{
            if(g!=gatt||destroyed)return;deadline=0;
            if(status!=BluetoothGatt.GATT_SUCCESS){fail("Bluetooth authentication or write failed ("+status+")");return;}
            try{engine.written();}catch(Exception e){fail("Recording transfer or local storage failed");}
        });}
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value,int status){
            byte[] bytes=Arrays.copyOf(value,value.length);
            handler.post(()->{
                if(g!=gatt||destroyed)return;deadline=0;
                if(status!=BluetoothGatt.GATT_SUCCESS){fail("Bluetooth read or authentication failed ("+status+")");return;}
                try{
                    if(c.getUuid().equals(Wire.INFO)){
                        RecorderInfo diagnostic=new RecorderInfo(bytes);
                        recorderStatus.update(diagnostic);
                        Settings.recorderSnapshot(RelayService.this,recorderStatus.text());
                        if(engine.awaitingInfo())engine.infoRead();else startTransfer(diagnostic.skip);
                    }else engine.read(c.getUuid().equals(Wire.META),bytes);
                }catch(Exception e){fail("Recording validation or local storage failed");}
            });
        }
    };
    @Override public void onDestroy(){
        destroyed=true;
        handler.removeCallbacksAndMessages(null);
        handler.post(()->{closeConnection();thread.quitSafely();});
        upload.shutdownNow();super.onDestroy();
    }
}
