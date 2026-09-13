package org.tomstout.hermesvoice;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.*;
/** Single-threaded GATT request state machine. No microphone is opened on the phone. */
public final class RelayService extends Service {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService upload=Executors.newSingleThreadExecutor();
    private BluetoothGatt gatt;private BluetoothGattCharacteristic ctrl,meta,data;
    private boolean destroyed=false;private long deadline=0;private int command=0;
    private String id;private int total,offset;private RandomAccessFile partial;private File partFile;
    static void start(Context c){if(!Settings.enabled(c))return;try{c.startForegroundService(new Intent(c,RelayService.class));}catch(RuntimeException e){Settings.status(c,"Open Hermes Voice and tap Start relay: "+e.getClass().getSimpleName());}}
    @Override public void onCreate(){super.onCreate();NotificationManager n=getSystemService(NotificationManager.class);
        n.createNotificationChannel(new NotificationChannel("relay","Voice recorder connection",NotificationManager.IMPORTANCE_LOW));
        PendingIntent p=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,"relay").setSmallIcon(R.drawable.ic_voice).setContentTitle("Hermes Voice relay")
            .setContentText("Ready for your voice button • microphone is on the recorder").setContentIntent(p).setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
        startForeground(12,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        handler.post(this::connect);handler.postDelayed(watchdog,2000);upload.submit(()->Uploader.drain(this));
    }
    @Override public int onStartCommand(Intent i,int flags,int startId){if(!Settings.enabled(this)){stopSelf();return START_NOT_STICKY;}return START_STICKY;}
    @Override public IBinder onBind(Intent i){return null;}
    private final Runnable watchdog=new Runnable(){public void run(){if(destroyed)return;if(deadline!=0&&SystemClock.elapsedRealtime()>deadline)fail("Bluetooth request timed out");handler.postDelayed(this,2000);}};
    private void waiting(){deadline=SystemClock.elapsedRealtime()+25000;}
    private void connect(){if(destroyed||!Settings.enabled(this)||gatt!=null)return;
        try{String address=Settings.prefs(this).getString("address",null);BluetoothAdapter a=getSystemService(BluetoothManager.class).getAdapter();
            if(address==null||a==null||!a.isEnabled()){handler.postDelayed(this::connect,10000);return;}
            gatt=a.getRemoteDevice(address).connectGatt(this,true,callback,BluetoothDevice.TRANSPORT_LE);
            Settings.status(this,"Waiting for voice button");
        }catch(Exception e){fail("Bluetooth unavailable: "+e.getMessage());}
    }
    private void closePartial(){if(partial!=null){try{partial.getFD().sync();partial.close();}catch(IOException ignored){}partial=null;}}
    private void fail(String reason){if(destroyed)return;Settings.status(this,reason+"; recordings retained");deadline=0;closePartial();ctrl=meta=data=null;
        if(gatt!=null){try{gatt.disconnect();gatt.close();}catch(RuntimeException ignored){}gatt=null;}handler.removeCallbacks(next);handler.postDelayed(this::connect,5000);}
    private void write(byte[] value){if(gatt==null||ctrl==null)return;command=value[0];waiting();try{if(gatt.writeCharacteristic(ctrl,value,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)!=BluetoothStatusCodes.SUCCESS)fail("Bluetooth write could not start");}catch(RuntimeException e){fail("Bluetooth permission or write error");}}
    private void read(BluetoothGattCharacteristic c){if(gatt==null)return;waiting();try{if(!gatt.readCharacteristic(c))fail("Bluetooth read could not start");}catch(RuntimeException e){fail("Bluetooth permission or read error");}}
    private final Runnable next=()->write(new byte[]{1});
    private void metadata(byte[] b)throws Exception{
        if(b.length!=20)throw new IOException("Invalid recorder metadata");String incoming=Wire.id(b,0);int length=Wire.le32(b,16);
        if(length==0){deadline=0;handler.postDelayed(next,2000);return;}
        if(length<228||length>Wire.MAX_FILE||incoming.equals("00000000-0000-0000-0000-000000000000"))throw new IOException("Invalid recording length or ID");
        closePartial();id=incoming;total=length;File dir=new File(getFilesDir(),"incoming");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create spool directory");
        partFile=new File(dir,id+".part");partial=new RandomAccessFile(partFile,"rw");
        if(partial.length()>total)partial.setLength(0);offset=(int)partial.length();
        if(offset==total){complete();return;}write(Wire.readCommand(offset));
    }
    private void received(byte[] b)throws Exception{
        if(partial==null||b.length==0||b.length>180||offset+b.length>total)throw new IOException("Invalid audio chunk");
        partial.seek(offset);partial.write(b);offset+=b.length;
        if(offset==total){complete();return;}if(offset%5760==0)partial.getFD().sync();write(Wire.readCommand(offset));
    }
    private void complete()throws Exception{
        closePartial();byte[] audio=Files.readAllBytes(partFile.toPath());
        try{if(!id.equals(Wire.validate(audio)))throw new IOException("Recording ID mismatch");}
        catch(Exception bad){/* Re-download next time; never ACK corrupt content. */if(!partFile.delete())Settings.status(this,"Remove corrupt .part file after exporting diagnostics");throw bad;}
        boolean fresh;try(QueueDb db=new QueueDb(this)){fresh=db.accept(audio);} // transaction committed before ACK below
        if(fresh)Feedback.received(this);Settings.status(this,"Phone saved "+id);write(Wire.ack(id));upload.submit(()->Uploader.drain(this));UploadJob.schedule(this);
    }
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state){handler.post(()->{
            if(g!=gatt||destroyed)return;if(status!=BluetoothGatt.GATT_SUCCESS||state==BluetoothProfile.STATE_DISCONNECTED){fail("Recorder disconnected");return;}
            if(state==BluetoothProfile.STATE_CONNECTED){waiting();if(!g.requestMtu(247)&&!g.discoverServices())fail("Cannot discover recorder");}
        });}
        @Override public void onMtuChanged(BluetoothGatt g,int mtu,int status){handler.post(()->{if(g==gatt){waiting();if(!g.discoverServices())fail("Service discovery could not start");}});}
        @Override public void onServicesDiscovered(BluetoothGatt g,int status){handler.post(()->{
            if(g!=gatt)return;BluetoothGattService service=g.getService(Wire.SERVICE);
            if(status!=BluetoothGatt.GATT_SUCCESS||service==null){fail("Recorder service not found");return;}
            ctrl=service.getCharacteristic(Wire.CONTROL);meta=service.getCharacteristic(Wire.META);data=service.getCharacteristic(Wire.DATA);
            if(ctrl==null||meta==null||data==null){fail("Recorder protocol mismatch");return;}deadline=0;handler.post(next);
        });}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){handler.post(()->{
            if(g!=gatt)return;deadline=0;if(status!=BluetoothGatt.GATT_SUCCESS){fail("Bluetooth authentication/write failed ("+status+")");return;}
            if(command==1)read(meta);else if(command==2)read(data);else if(command==3){if(partFile!=null)partFile.delete();partFile=null;handler.post(next);}
        });}
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value,int status){byte[] b=Arrays.copyOf(value,value.length);handler.post(()->{
            if(g!=gatt)return;deadline=0;if(status!=BluetoothGatt.GATT_SUCCESS){fail("Bluetooth read/authentication failed ("+status+")");return;}
            try{if(c.getUuid().equals(Wire.META))metadata(b);else if(c.getUuid().equals(Wire.DATA))received(b);}catch(Exception e){fail(e.getMessage());}
        });}
    };
    @Override public void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);closePartial();if(gatt!=null){try{gatt.disconnect();gatt.close();}catch(RuntimeException ignored){}}upload.shutdownNow();super.onDestroy();}
}
