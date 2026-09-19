package org.tomstout.hermesvoice;

import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/** Recorder transfer controller. All methods run on one worker thread.
 * Android and host simulations use the same controller and disk spool. */
final class TransferEngine {
    interface Port {
        void write(byte[] bytes);
        void read(boolean metadata);
        void idle();
        void progress(int received,int total);
        void saved(boolean fresh);
        void acknowledged();
        void refreshInfo();
        void problem(String code);
    }
    interface Inbox { boolean accept(byte[] bytes) throws Exception; }
    enum State { IDLE,NEXT,META,OFFSET,DATA,ACK,INFO,SKIP }
    private final Port port;
    private final Inbox inbox;
    private final File directory;
    private final Map<String,Integer> corruptAttempts=new HashMap<>();
    private State state=State.IDLE;
    private RandomAccessFile partial;
    private File partFile;
    private String id;
    private int total,offset;
    private boolean skipSupported;
    TransferEngine(File directory,Inbox inbox,Port port) {
        this.directory=directory;this.inbox=inbox;this.port=port;
    }
    void connected(boolean supportsSkip) throws IOException {
        close();skipSupported=supportsSkip;next();
    }
    void next() { state=State.NEXT;port.write(new byte[]{1}); }
    void disconnected() throws IOException { state=State.IDLE;close(); }
    boolean awaitingInfo() { return state==State.INFO; }
    void infoRead() {
        if(state!=State.INFO)throw new IllegalStateException("Unexpected recorder status");
        next();
    }
    void written() throws Exception {
        switch(state) {
            case NEXT -> {state=State.META;port.read(true);}
            case OFFSET -> {state=State.DATA;port.read(false);}
            case ACK -> {
                // A saved phone copy is not enough: wait for the recorder's ACK response.
                state=State.INFO;port.acknowledged();
                if(partFile!=null)Files.deleteIfExists(partFile.toPath());
                partFile=null;corruptAttempts.remove(id);
                // The last ACK may make the recorder disconnect before this read.
                port.refreshInfo();
            }
            case SKIP -> next();
            default -> throw new IOException("Unexpected Bluetooth write response");
        }
    }
    void read(boolean metadata,byte[] b) throws Exception {
        if(metadata&&state==State.META)metadata(b);
        else if(!metadata&&state==State.DATA)chunk(b);
        else throw new IOException("Unexpected Bluetooth read response");
    }
    private void metadata(byte[] b) throws Exception {
        if(b.length!=20)throw new IOException("Invalid recorder metadata");
        String incoming=Wire.id(b,0);int length=Wire.le32(b,16);
        if(length==0){state=State.IDLE;port.idle();return;}
        if(length<228||length>Wire.MAX_FILE||incoming.equals("00000000-0000-0000-0000-000000000000"))
            throw new IOException("Invalid recording length or ID");
        close();id=incoming;total=length;
        if(!directory.exists()&&!directory.mkdirs())throw new IOException("Spool unavailable");
        partFile=new File(directory,id+".part");partial=new RandomAccessFile(partFile,"rw");
        if(partial.length()>total)partial.setLength(0);
        offset=(int)partial.length();port.progress(offset,total);
        if(offset==total)complete();else requestChunk();
    }
    private void requestChunk(){state=State.OFFSET;port.write(Wire.readCommand(offset));}
    private void chunk(byte[] bytes) throws Exception {
        if(partial==null||bytes.length==0||bytes.length>180||offset+bytes.length>total)
            throw new IOException("Invalid audio chunk");
        partial.seek(offset);partial.write(bytes);offset+=bytes.length;
        port.progress(offset,total);
        if(offset==total)complete();
        else {if(offset%5760==0)partial.getFD().sync();requestChunk();}
    }
    private void complete() throws Exception {
        close();
        byte[] audio=Files.readAllBytes(partFile.toPath());
        try {
            if(!id.equals(Wire.validate(audio)))throw new IllegalArgumentException("Recording ID mismatch");
        } catch(IllegalArgumentException invalid) {
            int failures=corruptAttempts.merge(id,1,Integer::sum);
            // Preserve the last rejected bytes privately for deliberate recovery.
            Files.move(partFile.toPath(),new File(directory,id+".bad").toPath(),
                       java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if(failures>=3&&skipSupported) {
                port.problem("corrupt_recording_skipped");
                state=State.SKIP;byte[] skip=Wire.ack(id);skip[0]=4;port.write(skip);return;
            }
            throw new IOException("Corrupt recording retained; retry download");
        }
        boolean fresh=inbox.accept(audio); // durable transaction MUST finish first
        port.saved(fresh);state=State.ACK;port.write(Wire.ack(id));
    }
    private void close() throws IOException {
        if(partial!=null) {
            RandomAccessFile f=partial;partial=null;
            try {f.getFD().sync();} finally {f.close();}
        }
    }
}
