package org.tomstout.hermesvoice;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;

/** Host recorder simulator drives production transfer code and a real disk spool. */
public final class RelayEngineTest {
    static int cases;
    static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    static byte[] audio(int seed,int frames){
        byte[] b=new byte[64+164*frames];ByteBuffer v=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        b[0]='H';b[1]='V';b[2]='B';b[3]='1';b[4]=b[5]=1;b[6]=64;
        v.putInt(8,16000);v.putInt(12,frames*320);v.putInt(16,frames*164);
        byte[] id=Wire.uuidBytes(new UUID(123,seed).toString());System.arraycopy(id,0,b,24,16);
        CRC32 crc=new CRC32();crc.update(b,64,b.length-64);v.putInt(20,(int)crc.getValue());return b;
    }
    static final class Sim implements TransferEngine.Port,AutoCloseable {
        final Path root=Files.createTempDirectory("hermes-relay-");
        final List<byte[]> recordings=new ArrayList<>();
        final Map<String,byte[]> inbox=new HashMap<>();
        final Set<String> skipped=new HashSet<>();
        final ArrayDeque<Runnable> events=new ArrayDeque<>();
        TransferEngine engine;
        final RecorderStatus status=new RecorderStatus();
        final List<String> confirmedCounts=new ArrayList<>();
        byte[] selected;int at,acks,saves,steps,statusReads;
        boolean failCommit,lostAck,failRead,invalidChunk,skip=true,disconnectAfterAck,recordDuringAck,noInfo;
        boolean burst,early,duplicate,stale,gap;
        String afterAck;

        Exception error;
        Sim() throws IOException {newEngine();}
        void newEngine(){
            engine=new TransferEngine(root.toFile(),bytes->{
                if(failCommit)throw new IOException("disk full");
                String id=Wire.validate(bytes);byte[] previous=inbox.get(id);
                if(previous!=null&&!Arrays.equals(previous,bytes))throw new IOException("collision");
                inbox.put(id,bytes);return previous==null;
            },this);
        }
        void snapshot(){
            byte[] info=new byte[]{1,0,(byte)recordings.size(),0,0,0,0,0};
            status.update(new RecorderInfo(info));statusReads++;
        }
        void start() throws Exception {error=null;skipped.clear();if(!noInfo)snapshot();engine.connected(skip,burst);pump();}
        interface Op {void run()throws Exception;}
        void enqueue(Op op){events.add(()->{try{op.run();}catch(Exception e){error=e;events.clear();try{engine.disconnected();}catch(Exception ignored){}}});}
        void pump(){while(!events.isEmpty()){require(++steps<10000,"loop");events.remove().run();}}
        public void write(byte[] b){
            enqueue(()->{
                switch(b[0]){
                    case 1 -> selected=recordings.stream().filter(x->!skipped.contains(Wire.id(x,24))).findFirst().orElse(null);
                    case 2 -> at=Wire.le32(b,1);
                    case 9 -> {
                        int position=Wire.le32(b,1),token=Wire.le32(b,5);
                        for(int i=0;i<16&&position<selected.length;i++){
                            int count=Math.min(235,selected.length-position);byte[] packet=new byte[9+count];
                            ByteBuffer v=ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);v.put((byte)1).putInt(token).putInt(gap&&i==0?position+1:position);System.arraycopy(selected,position,packet,9,count);position+=count;
                            if(stale){byte[] old=packet.clone();ByteBuffer.wrap(old).order(ByteOrder.LITTLE_ENDIAN).putInt(1,token-1);if(early)engine.notification(old);else enqueue(()->engine.notification(old));}
                            if(early)engine.notification(packet);else enqueue(()->engine.notification(packet));
                            if(duplicate){if(early)engine.notification(packet);else enqueue(()->engine.notification(packet));}
                        }
                    }
                    case 3 -> {
                        String id=Wire.id(b,1);require(inbox.containsKey(id),"ACK before durable acceptance");
                        acks++;if(lostAck){lostAck=false;throw new IOException("ACK response lost");}
                        recordings.remove(selected);
                        if(recordDuringAck){recordDuringAck=false;recordings.add(audio(99,3));}
                    }
                    case 4 -> {skipped.add(Wire.id(b,1));require(recordings.contains(selected),"skip deleted data");}
                    default -> throw new AssertionError("unknown control");
                }
                engine.written();
            });
        }
        public void read(boolean metadata){
            enqueue(()->{
                byte[] b;
                if(metadata){
                    b=new byte[20];
                    if(selected!=null){System.arraycopy(selected,24,b,0,16);ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putInt(16,selected.length);}
                }else{
                    if(failRead&&at>=180){failRead=false;throw new IOException("radio disconnected");}
                    b=invalidChunk?new byte[0]:Arrays.copyOfRange(selected,at,Math.min(at+180,selected.length));
                }
                engine.read(metadata,b);
            });
        }
        public void idle(){}
        public void progress(int n,int total){require(n<=total,"progress overflow");}
        public void saved(boolean fresh){if(fresh)saves++;}
        public void acknowledged(){
            status.acknowledged();afterAck=status.text();confirmedCounts.add(afterAck);
        }
        public void refreshInfo(){
            enqueue(()->{
                if(disconnectAfterAck&&recordings.isEmpty())throw new IOException("Idle recorder disconnected");
                if(!noInfo)snapshot();
                engine.infoRead();
            });
        }
        public void problem(String code){require(code.equals("corrupt_recording_skipped"),"unexpected problem");}
        public void close()throws Exception{
            engine.disconnected();
            try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        }
    }
    public static void main(String[] args)throws Exception{
        try(Sim s=new Sim()){
            s.recordings.add(audio(1,20));s.start();require(s.acks==1&&s.inbox.size()==1&&s.recordings.isEmpty(),"normal transfer");cases++;
        }
        try(Sim s=new Sim()){
            s.recordings.add(audio(2,20));s.failRead=true;s.start();
            require(s.error!=null&&s.acks==0&&s.recordings.size()==1,"interrupted transfer retained");
            require(s.status.text().startsWith("Recorder queue: 1\n")&&s.confirmedCounts.isEmpty(),"interruption does not decrement queue");
            Path p=s.root.resolve(Wire.id(s.recordings.get(0),24)+".part");require(Files.size(p)==180,"partial persisted");
            s.newEngine();s.start();require(s.acks==1&&s.inbox.size()==1,"process restart resumes");cases++;
        }
        try(Sim s=new Sim()){
            s.recordings.add(audio(3,3));s.failCommit=true;s.start();
            require(s.acks==0&&s.inbox.isEmpty()&&s.recordings.size()==1,"disk full not ACKed");
            require(s.status.text().startsWith("Recorder queue: 1\n")&&s.confirmedCounts.isEmpty(),"failed save does not decrement queue");
            s.failCommit=false;s.start();require(s.acks==1,"retry disk full");cases++;
        }
        try(Sim s=new Sim()){
            s.recordings.add(audio(4,3));s.lostAck=true;s.start();
            require(s.inbox.size()==1&&s.recordings.size()==1,"lost ACK retained");
            require(s.status.text().startsWith("Recorder queue: 1\n")&&s.confirmedCounts.isEmpty(),"lost ACK response does not claim recorder removal");
            s.start();require(s.saves==1&&s.acks==2&&s.recordings.isEmpty(),"duplicate safe ACK");cases++;
        }
        try(Sim s=new Sim()){
            byte[] corrupt=audio(5,3);corrupt[64]^=1;s.recordings.add(corrupt);s.recordings.add(audio(6,3));
            s.start();s.start();s.start();
            require(s.inbox.size()==1&&s.acks==1&&s.recordings.size()==1,"corrupt skipped without deletion");
            require(Files.exists(s.root.resolve(Wire.id(corrupt,24)+".bad")),"bad bytes preserved");
            require(s.status.text().startsWith("Recorder queue: 1\n"),"idle after SKIP does not claim empty queue");cases++;
        }
        try(Sim s=new Sim()){
            byte[] corrupt=audio(7,3);corrupt[64]^=1;s.recordings.add(corrupt);s.skip=false;
            s.start();s.start();s.start();require(s.acks==0&&s.skipped.isEmpty(),"legacy never sent unsupported skip");cases++;
        }
        try(Sim s=new Sim()){
            s.recordings.add(audio(8,3));s.invalidChunk=true;s.start();require(s.acks==0&&s.error!=null,"empty chunk rejected");cases++;
        }
        try(Sim s=new Sim()){
            byte[] a=audio(9,3);s.recordings.add(a);
            Files.write(s.root.resolve(Wire.id(a,24)+".part"),new byte[a.length+20]);
            s.start();require(s.acks==1,"oversized stale partial restarted");cases++;
        }
        try(Sim s=new Sim()){
            byte[] a=audio(10,3);s.recordings.add(a);
            Files.write(s.root.resolve(Wire.id(a,24)+".part"),a);
            s.start();require(s.acks==1&&s.saves==1,"complete partial survives restart");cases++;
        }
        try(Sim s=new Sim()){
            for(int i=0;i<15;i++)s.recordings.add(audio(20+i,3));
            s.start();require(s.acks==15&&s.inbox.size()==15,"full recorder queue");
            require(s.status.text().startsWith("Recorder queue: 0\n")&&s.statusReads==16,"refresh after every ACK");
            for(int i=0;i<15;i++)require(s.confirmedCounts.get(i).startsWith("Recorder queue: "+(14-i)+" (estimated"),"remaining queue decreases after ACK");cases++;
        }
        try(Sim s=new Sim()){
            s.recordings.add(audio(40,3000));s.start();require(s.acks==1,"60 second maximum recording");cases++;
        }
        try(Sim s=new Sim()){
            boolean rejected=false;try{s.engine.written();}catch(IOException expected){rejected=true;}
            require(rejected,"out of order callback rejected");cases++;
        }
        try(Sim s=new Sim()){
            s.recordings.add(audio(50,3));s.disconnectAfterAck=true;s.start();
            require(s.error!=null&&s.recordings.isEmpty()&&s.inbox.size()==1,"final ACK followed by disconnect");
            require(s.status.text().startsWith("Recorder queue: 0 (estimated"),"last transfer does not leave stale one");
            s.disconnectAfterAck=false;s.start();
            require(s.status.text().startsWith("Recorder queue: 0\n"),"reconnection replaces estimate");cases++;
        }
        try(Sim s=new Sim()){
            s.recordings.add(audio(51,3));s.recordDuringAck=true;s.start();
            require(s.acks==2&&s.inbox.size()==2&&s.statusReads==3,"new recording discovered by fresh INFO");
            require(s.status.text().startsWith("Recorder queue: 0\n"),"concurrent new recording drained");cases++;
        }
        try(Sim s=new Sim()){
            s.recordings.add(audio(52,3));s.noInfo=true;s.start();
            require(s.acks==1&&s.status.text().equals("not available"),"missing INFO does not block transfer or invent queue");cases++;
        }
        for(boolean early:new boolean[]{false,true})try(Sim s=new Sim()){
            s.burst=true;s.early=early;s.recordings.add(audio(80,3000));s.start();
            require(s.error==null&&s.acks==1&&s.saves==1&&s.recordings.isEmpty(),"burst maximum duration, notification/write ordering "+early);cases++;
        }
        try(Sim s=new Sim()){
            s.burst=true;s.early=true;s.duplicate=true;s.stale=true;s.recordings.add(audio(81,50));s.start();
            require(s.error==null&&s.acks==1&&s.saves==1,"duplicate and stale-token notifications do not duplicate data");cases++;
        }
        try(Sim s=new Sim()){
            s.burst=true;s.gap=true;s.recordings.add(audio(82,50));s.start();require(s.error!=null&&s.acks==0&&s.inbox.isEmpty(),"burst gaps never ACK");
            s.burst=false;s.gap=false;s.start();require(s.error==null&&s.acks==1,"burst gap recovers using legacy pull");cases++;
        }
        try(Sim s=new Sim()){
            s.burst=true;s.failCommit=true;s.recordings.add(audio(83,50));s.start();require(s.error!=null&&s.acks==0&&s.recordings.size()==1,"burst preserves original when phone storage fails");
            s.failCommit=false;s.start();require(s.acks==1,"burst complete partial recovers after failed commit");cases++;
        }
        try(Sim s=new Sim()){
            s.burst=true;s.lostAck=true;s.recordings.add(audio(84,50));s.start();s.start();require(s.acks==2&&s.saves==1&&s.recordings.isEmpty(),"burst lost ACK is deduplicated");cases++;
        }
        for(int code:new int[]{400,409,413,415})require(UploadPolicy.action(code)==UploadPolicy.Action.HOLD,"hold "+code);
        for(int code:new int[]{401,403,404,411})require(UploadPolicy.action(code)==UploadPolicy.Action.CONFIGURATION,"config "+code);
        for(int code:new int[]{429,500,502,503,507})require(UploadPolicy.action(code)==UploadPolicy.Action.RETRY,"retry "+code);
        require(UploadPolicy.action(202)==UploadPolicy.Action.ACCEPT,"accept");cases++;
        require(UploadPolicy.delayMillis(1)==30000&&UploadPolicy.delayMillis(100)==3600000,"bounded backoff");cases++;
        byte[] info=new byte[32];info[0]=1;info[8]=1;info[10]=3;info[30]=1;
        require(new RecorderInfo(info).skip&&new RecorderInfo(info).text.contains("0.3.0"),"diagnostics extension");cases++;
        require(!new RecorderInfo(new byte[]{1,0,0,0,0,0,0,0}).skip,"legacy diagnostics");cases++;
        System.out.println("Passed "+cases+" Android relay simulation scenarios");
    }
}
