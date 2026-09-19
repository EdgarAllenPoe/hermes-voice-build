package org.tomstout.hermesvoice;

import android.content.Context;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Authenticated, bounded health check. Never stores credentials or raw exceptions as status. */
final class ServerHealth {
    static void check(Context context) {
        long revision=Settings.prefs(context).getLong("server_revision",0);
        HttpURLConnection connection=null;
        boolean ok=false;
        String message="Check the server address, access token and Tailscale.";
        try {
            URL endpoint=Endpoint.parse(Settings.endpoint(context));
            if(endpoint.getProtocol().equals("http")&&!Endpoint.tailInterfacePresent()){
                message="Turn on Tailscale on this phone, then try again.";return;
            }
            URL health=new URL(endpoint.getProtocol(),endpoint.getHost(),endpoint.getPort(),"/health");
            connection=(HttpURLConnection)health.openConnection();
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(10000);
            connection.setRequestProperty("Authorization","Bearer "+Settings.token(context));
            int code=connection.getResponseCode();
            if(code==401||code==403){message="The server rejected the access token. Update it in Diagnostics.";return;}
            if(code!=200){message="The server returned HTTP "+code+". Check the receiver, then try again.";return;}
            byte[] body;
            try(InputStream in=connection.getInputStream()){body=in.readNBytes(8193);}
            if(body.length>8192){message="The server returned an unexpected response.";return;}
            JSONObject result=new JSONObject(new String(body,StandardCharsets.UTF_8));
            ok="HVB1".equals(result.optString("protocol"));
            message=ok?"Server reachable and token accepted.":"The address did not return a Hermes Voice server.";
        }catch(Exception ignored) {
            message="The server could not be reached. Check Tailscale and server settings.";
        }finally {
            if(connection!=null)connection.disconnect();
            Settings.serverHealth(context,revision,ok,message);
        }
    }
}
