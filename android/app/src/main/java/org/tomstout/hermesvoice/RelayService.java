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
    private static volatile boolean running,connected;
    static boolean isRunning(){return running;}
    static boolean isConnected(){return running&&connected;}
    private HandlerThread thread;
    private Handler handler;
    private final ExecutorService upload=Executors.newSingleThreadExecutor();
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic ctrl,meta,data,info,catalog,config,stream;
    private RecorderInfo latestInfo;
    private String pendingAction,activeAction;
    private byte[] actionPayload;
    private int actionOffset;
    private boolean catalogRead,directConnect,stopTestAfterAction;
    private int failures;private boolean streamSubscribed,burstDisabled;private long transferStarted;
    private static final java.util.UUID CCC=java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    static void action(Context c,String name,byte[] payload){
        if(!Settings.enabled(c)){Settings.metric(c,"device_action","Start the relay first");return;}
        Intent i=new Intent(c,RelayService.class).setAction(name);if(payload!=null)i.putExtra("payload",payload);
        c.startForegroundService(i);
    }
    private void requestAction(String name,byte[] payload){
        if(name.equals("reconnect")){
            burstDisabled=false;
            pendingAction=activeAction=null;handler.removeCallbacks(next);deadline=0;closeConnection();directConnect=true;
            Settings.metric(this,"device_action","Reconnecting to recorder\u2026");connect();return;
        }
        if(name.equals("clear_partials")){
            pendingAction=activeAction=null;handler.removeCallbacks(next);deadline=0;closeConnection();
            try{File directory=new File(getFilesDir(),"incoming").getCanonicalFile();File[] files=directory.listFiles();int count=0;
                if(files!=null)for(File f:files){if(!f.getCanonicalFile().getParentFile().equals(directory)||!f.isFile()||!(f.getName().endsWith(".part")||f.getName().endsWith(".bad")))continue;
                    if(!f.delete())throw new IOException("Cannot remove unfinished transfer");count++;}
                Settings.metric(this,"transfer","Unfinished transfers cleared");Settings.metric(this,"device_action","Deleted "+count+" unfinished phone transfer(s). Recorder originals remain.");
            }catch(Exception e){Settings.metric(this,"device_action","Cleanup failed; remaining files retained");}
            connect();return;
        }
        if(name.equals("mic_stop")&&(pendingAction!=null||activeAction!=null)){stopTestAfterAction=true;return;}
        if(pendingAction!=null||activeAction!=null){Settings.metric(this,"device_action","Another recorder operation is in progress");return;}
        if(!connected||latestInfo==null){Settings.metric(this,"device_action","Recorder not connected. Reconnect and try again.");return;}
        if(!latestInfo.enhanced){Settings.metric(this,"device_action","This control requires recorder firmware 0.4.0 or newer");return;}
        pendingAction=name;actionPayload=payload;actionOffset=0;Settings.metric(this,"device_action","Working: "+name.replace('_',' '));
        if(deadline==0)beginAction();
    }
    private boolean beginAction(){
        if(pendingAction==null)return false;
        activeAction=pendingAction;pendingAction=null;handler.removeCallbacks(next);
        try{engine.disconnected();}catch(IOException e){fail("Could not pause transfer safely");return true;}
        if(activeAction.equals("refresh"))read(info);else sendAction();return true;
    }
    private void sendAction(){
        byte[] bytes;BluetoothGattCharacteristic target=ctrl;
        switch(activeAction){
            case "delete_recordings" -> {
                if(actionPayload==null||actionPayload.length==0||actionPayload.length%18!=0||actionPayload.length>270){fail("Invalid deletion request");return;}
                if(actionOffset>=actionPayload.length){read(info);return;}
                bytes=Arrays.copyOfRange(actionPayload,actionOffset,actionOffset+18);
                if(bytes[0]!=5){fail("Invalid deletion request");return;}
            }
            case "configure" -> {if(actionPayload==null||actionPayload.length!=6||config==null){fail("Invalid recorder settings");return;}bytes=actionPayload;target=config;}
            case "mic_start" -> bytes=new byte[]{8,1};
            case "mic_stop" -> bytes=new byte[]{8,0};
            default -> {fail("Unknown recorder operation");return;}
        }
        waiting();try{if(gatt.writeCharacteristic(target,bytes,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)!=BluetoothStatusCodes.SUCCESS)fail("Recorder operation could not start");}
        catch(SecurityException e){fail("Bluetooth permission was revoked");}
        catch(RuntimeException e){fail("Recorder operation could not start");}
    }
    private void afterStatus(){
        if(latestInfo.enhanced&&catalog!=null){catalogRead=true;read(catalog);}else continueTransfer();
    }
    private void continueTransfer(){
        if(activeAction!=null){Settings.metric(this,"device_action","Completed: "+activeAction.replace('_',' '));activeAction=null;actionPayload=null;}
        if(stopTestAfterAction){stopTestAfterAction=false;pendingAction="mic_stop";actionPayload=null;}
        if(beginAction())return;
        if(latestInfo!=null&&(latestInfo.micState==1||latestInfo.micState==2)){
            Settings.metric(this,"transfer","Microphone test \u00b7 no audio saved");handler.postDelayed(next,350);
        }else startTransfer(latestInfo!=null&&latestInfo.skip);
    }
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
        super.onCreate();running=true;connected=false;
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
                if(gatt!=null)try{gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED);}catch(SecurityException ignored){}
                handler.postDelayed(next,streamSubscribed?5000:2000);
            }
            public void progress(int received,int total){
                long now=SystemClock.elapsedRealtime();
                if(received==0||transferStarted==0){transferStarted=now;if(gatt!=null)try{gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);}catch(SecurityException ignored){}}
                if(received==total){Settings.metric(RelayService.this,"last_transfer_ms",Long.toString(now-transferStarted));transferStarted=0;}
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
                Settings.recorderUpdate(RelayService.this,recorderStatus,false);
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
        if(!Settings.enabled(this)){stopSelf();return START_NOT_STICKY;}
        if(i!=null&&i.getAction()!=null){String action=i.getAction();byte[] payload=i.getByteArrayExtra("payload");handler.post(()->requestAction(action,payload));}return START_STICKY;
    }
    @Override public IBinder onBind(Intent i){return null;}
    private final Runnable watchdog=new Runnable(){public void run(){
        if(destroyed)return;
        if(deadline!=0&&SystemClock.elapsedRealtime()>deadline)fail("Bluetooth request timed out");
        handler.postDelayed(this,2000);
    }};
    private final Runnable next=()->{if(!destroyed&&gatt!=null&&!beginAction()){if(info!=null)read(info);else engine.next();}};
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
            gatt=a.getRemoteDevice(address).connectGatt(this,!directConnect,callback,BluetoothDevice.TRANSPORT_LE);directConnect=false;
            deadline=SystemClock.elapsedRealtime()+45000;
            Settings.metric(this,"connection","Waiting for recorder");
        }catch(SecurityException e){fail("Bluetooth permission was revoked");}catch(Exception e){fail("Bluetooth unavailable");}
    }
    private void closeConnection(){
        connected=false;
        try{engine.disconnected();}catch(IOException e){Settings.metric(this,"recorder_problem","spool_sync_failed");}
        ctrl=meta=data=info=catalog=config=stream=null;streamSubscribed=false;transferStarted=0;latestInfo=null;catalogRead=false;
        if(gatt!=null){try{gatt.disconnect();gatt.close();}catch(SecurityException ignored){}catch(RuntimeException ignored){}gatt=null;}
    }
    private void fail(String reason){
        if(destroyed)return;
        if(engine!=null&&engine.isBurst()){burstDisabled=true;Settings.metric(this,"transfer_mode","Reliable pull fallback after interrupted burst");}
        if(activeAction!=null||pendingAction!=null)Settings.metric(this,"device_action","Operation interrupted: "+reason+". Refresh the recorder before retrying.");
        activeAction=pendingAction=null;actionPayload=null;
        Settings.status(this,reason+"; recordings retained");Settings.metric(this,"connection","Disconnected");
        deadline=0;closeConnection();handler.removeCallbacks(next);handler.postDelayed(this::connect,Math.min(60000,5000L*(1+failures++)));
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
        try{boolean burst=streamSubscribed&&latestInfo!=null&&latestInfo.burst&&!burstDisabled;Settings.metric(this,"transfer_mode",burst?"Batched Bluetooth notifications":"Reliable Bluetooth pull");engine.connected(skip,burst);}catch(IOException e){fail("Phone spool could not open");}
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
            catalog=service.getCharacteristic(Wire.INVENTORY);config=service.getCharacteristic(Wire.CONFIG);stream=service.getCharacteristic(Wire.STREAM);
            if(ctrl==null||meta==null||data==null){fail("Recorder protocol mismatch");return;}
            connected=true;failures=0;
            if(getSharedPreferences("diagnostics",MODE_PRIVATE).getString("device_action","").startsWith("Reconnecting"))Settings.metric(RelayService.this,"device_action","Recorder reconnected");
            getSharedPreferences("diagnostics",MODE_PRIVATE).edit().putLong("recorder_contact_at",System.currentTimeMillis()).apply();
            Settings.metric(RelayService.this,"connection","Connected");
            if(stream!=null){
                BluetoothGattDescriptor descriptor=stream.getDescriptor(CCC);
                try{if(descriptor!=null&&g.setCharacteristicNotification(stream,true)){
                    waiting();if(g.writeDescriptor(descriptor,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)==BluetoothStatusCodes.SUCCESS)return;
                }}catch(SecurityException e){fail("Bluetooth permission was revoked");return;}
            }
            if(info!=null)read(info);else startTransfer(false);
        });}
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor descriptor,int status){handler.post(()->{
            if(g!=gatt||destroyed)return;deadline=0;streamSubscribed=status==BluetoothGatt.GATT_SUCCESS;
            if(info!=null)read(info);else startTransfer(false);
        });}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic characteristic,byte[] value){
            byte[] packet=Arrays.copyOf(value,value.length);handler.post(()->{
                if(g!=gatt||destroyed||!characteristic.getUuid().equals(Wire.STREAM))return;
                if(packet.length==1&&packet[0]==2){
                    if(deadline==0&&engine.isIdle()&&pendingAction==null&&activeAction==null){handler.removeCallbacks(next);handler.post(next);}return;
                }
                try{if(engine.isBurst()){waiting();engine.notification(packet);}}
                catch(Exception e){fail("Bluetooth burst interrupted; resuming saved partial transfer");}
            });
        }
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){handler.post(()->{
            if(g!=gatt||destroyed)return;deadline=0;
            if(status!=BluetoothGatt.GATT_SUCCESS){fail("Bluetooth authentication or write failed ("+status+")");return;}
            if(activeAction!=null){if(activeAction.equals("delete_recordings")){actionOffset+=18;sendAction();}else read(info);return;}
            if(beginAction())return;
            try{engine.written();if(engine.isBurst())waiting();}catch(Exception e){fail("Recording transfer or local storage failed");}
        });}
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value,int status){
            byte[] bytes=Arrays.copyOf(value,value.length);
            handler.post(()->{
                if(g!=gatt||destroyed)return;deadline=0;
                if(status!=BluetoothGatt.GATT_SUCCESS){fail("Bluetooth read or authentication failed ("+status+")");return;}
                if(pendingAction!=null&&activeAction==null){beginAction();return;}
                try{
                    if(c.getUuid().equals(Wire.INVENTORY)&&catalogRead){
                        catalogRead=false;java.util.List<RecorderInventory.Entry> entries=RecorderInventory.parse(bytes);
                        getSharedPreferences("diagnostics",MODE_PRIVATE).edit().putString("inventory",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)).putLong("inventory_at",System.currentTimeMillis()).apply();
                        try(QueueDb db=new QueueDb(RelayService.this)){db.observed(entries);}continueTransfer();
                    }else if(c.getUuid().equals(Wire.INFO)){
                        RecorderInfo diagnostic=new RecorderInfo(bytes);
                        latestInfo=diagnostic;recorderStatus.update(diagnostic);
                        Settings.recorderUpdate(RelayService.this,recorderStatus,true);
                        if(diagnostic.lowBattery)notifyLowBattery();
                        if(activeAction!=null)afterStatus();else if(engine.awaitingInfo())engine.infoRead();else afterStatus();
                    }else engine.read(c.getUuid().equals(Wire.META),bytes);
                }catch(Exception e){fail("Recording validation or local storage failed");}
            });
        }
    };
    private long lastBatteryAlert;
    private void notifyLowBattery(){
        if(SystemClock.elapsedRealtime()-lastBatteryAlert<3_600_000&&lastBatteryAlert!=0)return;lastBatteryAlert=SystemClock.elapsedRealtime();
        NotificationManager n=getSystemService(NotificationManager.class);
        n.createNotificationChannel(new NotificationChannel("battery","Recorder battery",NotificationManager.IMPORTANCE_DEFAULT));
        try{n.notify(13,new Notification.Builder(this,"battery").setSmallIcon(R.drawable.ic_voice).setContentTitle("Recorder battery is low").setContentText("Connect the recorder to USB to charge.").setAutoCancel(true).build());}catch(SecurityException ignored){}
    }
    @Override protected void dump(FileDescriptor fd,PrintWriter out,String[] args){
        out.println(DashboardReader.read(this,false).diagnostics());
        android.content.SharedPreferences d=getSharedPreferences("diagnostics",MODE_PRIVATE);
        for(String key:new String[]{"enhanced","mic_state","mic_level","mic_peak","mic_frames","silence_ms","threshold","manual","free_slots","inventory_at","device_action","transfer_mode","last_transfer_ms"})out.println(key+"="+d.getAll().get(key));
    }
    @Override public void onDestroy(){
        destroyed=true;running=false;connected=false;
        handler.removeCallbacksAndMessages(null);
        handler.post(()->{closeConnection();thread.quitSafely();});
        upload.shutdownNow();super.onDestroy();
    }
}
