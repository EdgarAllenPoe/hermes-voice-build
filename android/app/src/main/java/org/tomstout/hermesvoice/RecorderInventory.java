package org.tomstout.hermesvoice;
import java.util.*;
/** Snapshot catalog. Deletions carry BOTH physical slot and immutable recording ID. */
final class RecorderInventory {
    record Entry(int slot,boolean damaged,String id,int bytes,int durationMs,long sequence) {
        byte[] deleteCommand(){byte[] b=new byte[18];b[0]=5;b[1]=(byte)slot;System.arraycopy(Wire.uuidBytes(id),0,b,2,16);return b;}
    }
    static List<Entry> parse(byte[] b){
        if(b.length<4||b[0]!=1||b[2]!=0||b[3]!=0||(b[1]&255)>15||b.length!=4+(b[1]&255)*30)throw new IllegalArgumentException("Invalid recorder catalog");
        List<Entry> out=new ArrayList<>();Set<Integer> slots=new HashSet<>();
        for(int p=4;p<b.length;p+=30){int slot=b[p]&255;boolean damaged=b[p+1]==1;int size=Wire.le32(b,p+18),duration=Wire.le32(b,p+22);
            if(slot>=15||!slots.add(slot)||(b[p+1]!=0&&b[p+1]!=1)||(!damaged&&(size<228||size>Wire.MAX_FILE||duration<20||duration>60000)))throw new IllegalArgumentException("Invalid catalog entry");
            out.add(new Entry(slot,damaged,Wire.id(b,p+2),size,duration,Integer.toUnsignedLong(Wire.le32(b,p+26))));
        }
        out.sort(Comparator.comparingLong(Entry::sequence));return out;
    }
}
