package org.tomstout.hermesvoice;
import java.nio.*;
import java.security.*;
import java.util.*;
import java.util.zip.CRC32;
/** Pure Java protocol validation; tested independently of Android. */
public final class Wire {
    public static final int MAX_FILE=492064;
    public static final UUID SERVICE=uuid(1), CONTROL=uuid(2), META=uuid(3), DATA=uuid(4), INFO=uuid(5);
    public static UUID uuid(int n) { return UUID.fromString(String.format(Locale.ROOT,"58ef%04x-35c8-4c31-89aa-81f763051da1",n)); }
    public static String id(byte[] b,int off) {
        ByteBuffer v=ByteBuffer.wrap(b,off,16).order(ByteOrder.BIG_ENDIAN);
        return new UUID(v.getLong(),v.getLong()).toString();
    }
    public static byte[] uuidBytes(String s) {
        UUID u=UUID.fromString(s);return ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();
    }
    public static int le32(byte[] b,int off) {return ByteBuffer.wrap(b,off,4).order(ByteOrder.LITTLE_ENDIAN).getInt();}
    public static byte[] readCommand(int offset) {return ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN).put((byte)2).putInt(offset).array();}
    public static byte[] ack(String id) {byte[] b=new byte[17];b[0]=3;System.arraycopy(uuidBytes(id),0,b,1,16);return b;}
    private static void require(boolean b,String s) {if(!b)throw new IllegalArgumentException(s);}
    public static String validate(byte[] b) {
        require(b.length>=228&&b.length<=MAX_FILE,"Recording size out of range");
        require(b[0]=='H'&&b[1]=='V'&&b[2]=='B'&&b[3]=='1',"Not HVB1");
        require(b[4]==1&&b[5]==1&&b[6]==64&&b[7]==0&&le32(b,8)==16000,"Unsupported audio format");
        int samples=le32(b,12),length=le32(b,16);
        require(samples>0&&samples<=960000&&samples%320==0,"Invalid sample count");
        require(length==samples/320*164&&b.length==64+length,"Truncated recording");
        require((le32(b,40)&~3)==0,"Unsupported flags");
        for(int i=56;i<64;i++)require(b[i]==0,"Reserved header bytes");
        CRC32 c=new CRC32();c.update(b,64,length);require(c.getValue()==Integer.toUnsignedLong(le32(b,20)),"Checksum mismatch");
        for(int i=64;i<b.length;i+=164)require((b[i+2]&255)<=88&&b[i+3]==0&&(b[i+163]&240)==0,"Malformed ADPCM frame");
        String id=id(b,24);require(!id.equals("00000000-0000-0000-0000-000000000000"),"Empty ID");return id;
    }
    public static String hash(byte[] data) {
        try {byte[] b=MessageDigest.getInstance("SHA-256").digest(data);StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.ROOT,"%02x",x&255));return s.toString();}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private Wire() {}
}
