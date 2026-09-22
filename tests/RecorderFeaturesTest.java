package org.tomstout.hermesvoice;
import java.nio.*;import java.nio.file.*;import java.util.*;
public class RecorderFeaturesTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  byte[] info=new byte[64];info[0]=1;info[8]=1;info[30]=31;info[32]=2;info[33]=2;info[34]=1;info[35]=2;info[48]=1;info[49]=14;
  ByteBuffer v=ByteBuffer.wrap(info).order(ByteOrder.LITTLE_ENDIAN);v.putShort(4,(short)4020);v.putInt(36,100);v.putShort(40,(short)120);v.putShort(44,(short)4000);v.putShort(46,(short)40);
  RecorderInfo i=new RecorderInfo(info);check(i.enhanced&&i.manual&&i.lowBattery&&i.batteryMv==4020&&i.level==120&&i.silenceMs==4000&&i.freeSlots==14&&i.batteryText().contains("Charging"));
  check(!new RecorderInfo(Arrays.copyOf(info,32)).enhanced);
  byte[] catalog=new byte[34];catalog[0]=1;catalog[1]=1;catalog[4]=3;String id="01234567-89ab-4def-8123-456789abcdef";System.arraycopy(Wire.uuidBytes(id),0,catalog,6,16);v=ByteBuffer.wrap(catalog).order(ByteOrder.LITTLE_ENDIAN);v.putInt(22,228);v.putInt(26,20);v.putInt(30,7);
  RecorderInventory.Entry e=RecorderInventory.parse(catalog).get(0);check(e.slot()==3&&e.id().equals(id)&&e.durationMs()==20&&e.deleteCommand()[1]==3&&Wire.id(e.deleteCommand(),2).equals(id));
  catalog[4]=15;try{RecorderInventory.parse(catalog);throw new AssertionError();}catch(IllegalArgumentException expected){}
  byte[] audio=Files.readAllBytes(Path.of(args[0])),expected=Files.readAllBytes(Path.of(args[1]));short[] pcm=AudioDecoder.decode(audio);check(pcm.length*2==expected.length);v=ByteBuffer.wrap(expected).order(ByteOrder.LITTLE_ENDIAN);for(short sample:pcm)check(sample==v.getShort());
  audio[audio.length-1]^=1;try{AudioDecoder.decode(audio);throw new AssertionError();}catch(IllegalArgumentException expectedFailure){}
  System.out.println("Recorder extension, inventory guards and playback PCM passed");
 }
}
