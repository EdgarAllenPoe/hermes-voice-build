package org.tomstout.hermesvoice;
import android.content.Context;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
final class Uploader {
    private static final AtomicBoolean busy=new AtomicBoolean(false);
    static void drain(Context ctx){
        if(!Settings.enabled(ctx)||!busy.compareAndSet(false,true))return;
        try(QueueDb db=new QueueDb(ctx)){
            URL endpoint=Endpoint.parse(Settings.endpoint(ctx));String token=Settings.token(ctx);
            if(endpoint.getProtocol().equals("http")&&!Endpoint.tailInterfacePresent())
                throw new IOException("Tailscale interface unavailable");
            for(int n=0;n<30&&Settings.enabled(ctx)&&!Thread.currentThread().isInterrupted();n++){
                QueueDb.Item m=db.next();if(m==null)break;HttpURLConnection conn=null;
                try{
                    conn=(HttpURLConnection)endpoint.openConnection();conn.setInstanceFollowRedirects(false);
                    conn.setConnectTimeout(15000);conn.setReadTimeout(30000);
                    conn.setRequestMethod("POST");conn.setDoOutput(true);
                    conn.setRequestProperty("Authorization","Bearer "+token);
                    conn.setRequestProperty("Content-Type","application/octet-stream");
                    conn.setFixedLengthStreamingMode(m.audio().length);
                    try(OutputStream o=conn.getOutputStream()){o.write(m.audio());}
                    int code=conn.getResponseCode();
                    UploadPolicy.Action action=UploadPolicy.action(code);
                    if(action!=UploadPolicy.Action.ACCEPT){
                        db.failed(m,"HTTP "+code,action==UploadPolicy.Action.HOLD);
                        Settings.status(ctx,action==UploadPolicy.Action.HOLD?
                            "One recording held for review; uploading others":"Upload delayed: HTTP "+code);
                        if(action==UploadPolicy.Action.HOLD)continue;
                        break;
                    }
                    byte[] body;try(InputStream in=conn.getInputStream()){body=in.readNBytes(8193);}
                    if(body.length>8192)throw new IOException("Oversized acknowledgement");
                    JSONObject ack=new JSONObject(new String(body,StandardCharsets.UTF_8));
                    if(!ack.optBoolean("accepted",false)||!m.id().equals(ack.optString("id"))||!m.sha().equals(ack.optString("sha256")))
                        throw new IOException("Acknowledgement mismatch");
                    db.sent(m.id());Feedback.accepted(ctx);
                    Settings.metric(ctx,"last_upload",Long.toString(System.currentTimeMillis()));
                    Settings.status(ctx,"Recording accepted by Hermes computer");
                }catch(Exception e){
                    db.failed(m,"Network or acknowledgement failure",false);
                    Settings.status(ctx,"Upload delayed; recording retained");break;
                }finally{if(conn!=null)conn.disconnect();}
            }
        }catch(Exception e){Settings.status(ctx,"Check server settings, token, and Tailscale; recordings retained");}
        finally{busy.set(false);}
    }
}
