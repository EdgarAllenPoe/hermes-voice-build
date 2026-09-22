package org.tomstout.hermesvoice;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.zip.CRC32;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class QueueDbTest {
    Context context;
    byte[] audio(int seed){
        byte[] b=new byte[228];ByteBuffer v=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        b[0]='H';b[1]='V';b[2]='B';b[3]='1';b[4]=b[5]=1;b[6]=64;
        v.putInt(8,16000);v.putInt(12,320);v.putInt(16,164);
        System.arraycopy(Wire.uuidBytes(new UUID(99,seed).toString()),0,b,24,16);
        CRC32 crc=new CRC32();crc.update(b,64,164);v.putInt(20,(int)crc.getValue());return b;
    }
    @Before public void before(){
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Tests may only clear the isolated CI app database",context.getPackageName().endsWith(".ci"));
        context.deleteDatabase("voice_queue.db");
    }
    @After public void after(){context.deleteDatabase("voice_queue.db");}
    @Test public void committedAudioSurvivesReopen(){
        byte[] b=audio(1);try(QueueDb d=new QueueDb(context)){assertTrue(d.accept(b));}
        try(QueueDb d=new QueueDb(context)){assertArrayEquals(b,d.next().audio());assertFalse(d.accept(b));}
    }
    @Test public void heldMessageDoesNotBlockTheNext(){
        byte[] first=audio(1),second=audio(2);
        try(QueueDb d=new QueueDb(context)){
            d.accept(first);d.failed(d.next(),"HTTP 409",true);d.accept(second);
            assertEquals(Wire.id(second,24),d.next().id());
            d.sent(d.next().id());assertNull(d.next());
            d.retryHeld();assertEquals(Wire.id(first,24),d.next().id());
        }
    }
    @Test public void retryDelayAndManualRetry(){
        try(QueueDb d=new QueueDb(context)){
            d.accept(audio(1));d.failed(d.next(),"HTTP 503",false);assertNull(d.next());
            d.retryPending();assertNotNull(d.next());assertEquals(1,d.next().attempts());
        }
    }
    @Test public void sentReceiptRetainsDeduplication(){
        byte[] b=audio(1);
        try(QueueDb d=new QueueDb(context)){d.accept(b);d.sent(d.next().id());assertNull(d.next());}
        try(QueueDb d=new QueueDb(context)){assertFalse(d.accept(b));assertNull(d.next());}
    }
    @Test public void versionOneUpgradePreservesOriginalAudio(){
        byte[] b=audio(5);String id=Wire.id(b,24);
        try(SQLiteDatabase old=context.openOrCreateDatabase("voice_queue.db",0,null)){
            old.execSQL("CREATE TABLE messages(id TEXT PRIMARY KEY,sha TEXT NOT NULL,audio BLOB,state TEXT NOT NULL,created INTEGER NOT NULL,error TEXT)");
            old.execSQL("INSERT INTO messages VALUES(?,?,?,'pending',1,NULL)",new Object[]{id,Wire.hash(b),b});
            old.setVersion(1);
        }
        try(QueueDb d=new QueueDb(context)){
            assertArrayEquals(b,d.next().audio());assertEquals(0,d.next().attempts());
            assertEquals(3,d.getReadableDatabase().getVersion());
            assertFalse(d.accept(b));
        }
    }
    @Test public void fullSynchronizationIsEnabled(){
        for(int reopen=0;reopen<2;reopen++){
            try(QueueDb d=new QueueDb(context)){
                d.accept(audio(reopen+1));
                try(Cursor c=d.getWritableDatabase().rawQuery("PRAGMA synchronous",null)){
                    assertTrue(c.moveToFirst());assertEquals(2,c.getInt(0));
                }
                try(Cursor c=d.getWritableDatabase().rawQuery("PRAGMA journal_mode",null)){
                    assertTrue(c.moveToFirst());assertEquals("wal",c.getString(0));
                }
            }
        }
    }

    @Test public void discardedRecordingCannotReappearOrUpload(){
        byte[] b=audio(20);try(QueueDb db=new QueueDb(context)){db.accept(b);db.discard(Wire.id(b,24));assertNull(db.next());assertNull(db.playback(Wire.id(b,24)));assertFalse(db.accept(b));assertEquals("discarded",db.history().get(0).state());}
    }
    @Test public void recorderInventoryBecomesDurablePhoneCopy(){
        byte[] b=audio(21);String id=Wire.id(b,24);try(QueueDb db=new QueueDb(context)){
            db.observed(java.util.List.of(new RecorderInventory.Entry(0,false,id,b.length,20,1)));assertEquals("recorder",db.history().get(0).state());assertNull(db.next());
            assertTrue(db.accept(b));assertArrayEquals(b,db.next().audio());assertEquals(20,db.history().get(0).durationMs());
            db.observed(java.util.List.of());assertEquals(1,db.history().size());
        }
    }
    @Test public void deliveredAudioRetentionExpiresWithoutLosingReceipt(){
        context.getSharedPreferences("settings",0).edit().putBoolean("keep_audio",true).commit();byte[] b=audio(22);String id=Wire.id(b,24);
        try(QueueDb db=new QueueDb(context)){db.accept(b);db.sent(id);assertArrayEquals(b,db.playback(id));db.getWritableDatabase().execSQL("UPDATE messages SET retain_until=1 WHERE id=?",new Object[]{id});assertNull(db.playback(id));assertFalse(db.accept(b));assertEquals("sent",db.history().get(0).state());}
        finally{context.getSharedPreferences("settings",0).edit().putBoolean("keep_audio",false).commit();}
    }
    @Test public void deletionDoesNotEraseSentReceipt(){byte[] b=audio(23);String id=Wire.id(b,24);try(QueueDb db=new QueueDb(context)){db.accept(b);db.sent(id);db.discard(id);assertEquals("sent",db.history().get(0).state());}}
    @Test public void versionTwoUpgradePreservesHeldRecording(){
        byte[] b=audio(24);try(SQLiteDatabase old=context.openOrCreateDatabase("voice_queue.db",0,null)){
            old.execSQL("CREATE TABLE messages(id TEXT PRIMARY KEY,sha TEXT NOT NULL,audio BLOB,state TEXT NOT NULL,created INTEGER NOT NULL,error TEXT,attempts INTEGER NOT NULL DEFAULT 0,next_try INTEGER NOT NULL DEFAULT 0)");
            old.execSQL("INSERT INTO messages(id,sha,audio,state,created) VALUES(?,?,?,'held',1)",new Object[]{Wire.id(b,24),Wire.hash(b),b});old.setVersion(2);
        }
        try(QueueDb db=new QueueDb(context)){assertArrayEquals(b,db.playback(Wire.id(b,24)));assertEquals(1,db.stats().held());db.retry(Wire.id(b,24));assertNotNull(db.next());}
    }
}
