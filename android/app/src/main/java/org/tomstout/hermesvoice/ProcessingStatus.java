package org.tomstout.hermesvoice;
import android.content.Context;
import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;
import java.util.Set;import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
final class ProcessingStatus {
    private static final AtomicBoolean busy=new AtomicBoolean();
    static void refresh(Context c){
        if(!Settings.enabled(c)||!busy.compareAndSet(false,true))return;
        try(QueueDb db=new QueueDb(c)){
            URL endpoint=Endpoint.parse(Settings.endpoint(c));if(endpoint.getProtocol().equals("http")&&!Endpoint.tailInterfacePresent())return;
            String token=Settings.token(c);
            for(String[] item:db.statusCandidates()){
                HttpURLConnection conn=(HttpURLConnection)new URL(endpoint,"/v1/messages/"+item[0]).openConnection();
                try{conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(8000);conn.setReadTimeout(8000);conn.setRequestProperty("Authorization","Bearer "+token);
                    int code=conn.getResponseCode();
                    if(code==404){Settings.metric(c,"processing_status","Server status unavailable; upload receipt remains valid");break;}
                    if(code!=200)break;
                    byte[] bytes;try(InputStream in=conn.getInputStream()){bytes=in.readNBytes(8193);}if(bytes.length>8192)throw new IOException("Oversized status");
                    JSONObject value=new JSONObject(new String(bytes,StandardCharsets.UTF_8));String state=value.getString("state");
                    if(!item[0].equals(value.getString("id"))||!item[1].equals(value.getString("sha256"))||!Set.of("queued","transcribing","review","ready","delivering","done","failed","uncertain","rejected").contains(state))throw new IOException("Invalid processing status");
                    db.serverStatus(item[0],state);Settings.metric(c,"processing_status","Message processing status checked");
                }finally{conn.disconnect();}
            }
        }catch(Exception ignored){Settings.metric(c,"processing_status","Processing check unavailable; previous receipts retained");}finally{busy.set(false);}
    }
}
