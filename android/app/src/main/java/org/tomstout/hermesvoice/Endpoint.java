package org.tomstout.hermesvoice;
import java.net.*;
import java.util.*;
/** No redirects, public Internet hosts, userinfo, query strings, or arbitrary HTTP. */
public final class Endpoint {
    public static boolean tailAddress(String h) {
        if(h==null||!h.matches("100\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}"))return false;
        String[] p=h.split("\\.");int second=Integer.parseInt(p[1]);
        return second>=64&&second<=127&&Integer.parseInt(p[2])<=255&&Integer.parseInt(p[3])<=255;
    }
    public static URL parse(String input) throws Exception {
        URI u=new URI(input.trim());String h=u.getHost();
        if(h==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||!"/v1/voice".equals(u.getPath()))throw new IllegalArgumentException("Use an exact /v1/voice URL without credentials, query, or fragment");
        boolean secure="https".equals(u.getScheme())&&h.toLowerCase(Locale.ROOT).endsWith(".ts.net")&&(u.getPort()==-1||u.getPort()==443);
        boolean privateHttp="http".equals(u.getScheme())&&tailAddress(h)&&u.getPort()==8765;
        if(!secure&&!privateHttp)throw new IllegalArgumentException("Use HTTPS on your .ts.net name, or HTTP on a Tailscale 100.x address at port 8765");
        return u.toURL();
    }
    /** Conservative safety check for the optional HTTP fallback; not a VPN identity proof. */
    public static boolean tailInterfacePresent() throws SocketException {
        Enumeration<NetworkInterface> it=NetworkInterface.getNetworkInterfaces();
        while(it!=null&&it.hasMoreElements()) {
            NetworkInterface n=it.nextElement();if(!n.isUp()||n.isLoopback())continue;
            Enumeration<InetAddress> a=n.getInetAddresses();while(a.hasMoreElements())if(tailAddress(a.nextElement().getHostAddress()))return true;
        }
        return false;
    }
    private Endpoint(){}
}
