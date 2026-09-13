package org.tomstout.hermesvoice;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
/** SQLite FULL synchronous commit precedes every recorder ACK. */
final class QueueDb extends SQLiteOpenHelper {
    QueueDb(Context c){super(c,"voice_queue.db",null,1);setWriteAheadLoggingEnabled(true);}
    @Override public void onConfigure(SQLiteDatabase db){db.execSQL("PRAGMA synchronous=FULL");}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE messages(id TEXT PRIMARY KEY,sha TEXT NOT NULL,audio BLOB,state TEXT NOT NULL,created INTEGER NOT NULL,error TEXT)");}
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){throw new IllegalStateException("Migration required; do not delete the queue");}
    synchronized boolean accept(byte[] audio) {
        String id=Wire.validate(audio),hash=Wire.hash(audio);SQLiteDatabase db=getWritableDatabase();boolean fresh=false;db.beginTransaction();
        try {
            try(Cursor r=db.rawQuery("SELECT sha FROM messages WHERE id=?",new String[]{id})) {
                if(r.moveToFirst()) {if(!hash.equals(r.getString(0)))throw new IllegalArgumentException("Message ID collision");db.setTransactionSuccessful();return false;}
            }
            try(Cursor r=db.rawQuery("SELECT COALESCE(SUM(length(audio)),0) FROM messages",null)){r.moveToFirst();if(r.getLong(0)+audio.length>100L*1024*1024)throw new IllegalStateException("Phone queue is full; recording stays on device");}
            ContentValues v=new ContentValues();v.put("id",id);v.put("sha",hash);v.put("audio",audio);v.put("state","pending");v.put("created",System.currentTimeMillis());db.insertOrThrow("messages",null,v);db.setTransactionSuccessful();fresh=true;
        }finally{db.endTransaction();}return fresh;
    }
    record Item(String id,String sha,byte[] audio){}
    synchronized Item next(){try(Cursor r=getReadableDatabase().rawQuery("SELECT id,sha,audio FROM messages WHERE state='pending' ORDER BY created LIMIT 1",null)){return r.moveToFirst()?new Item(r.getString(0),r.getString(1),r.getBlob(2)):null;}}
    synchronized void sent(String id){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{ContentValues v=new ContentValues();v.put("state","sent");v.putNull("audio");v.putNull("error");d.update("messages",v,"id=?",new String[]{id});d.setTransactionSuccessful();}finally{d.endTransaction();}}
    synchronized void error(String id,String text){ContentValues v=new ContentValues();v.put("error",text);getWritableDatabase().update("messages",v,"id=?",new String[]{id});}
    synchronized String summary(){try(Cursor r=getReadableDatabase().rawQuery("SELECT state,COUNT(*) FROM messages GROUP BY state",null)){StringBuilder s=new StringBuilder();while(r.moveToNext())s.append(r.getString(0)).append(": ").append(r.getInt(1)).append("  ");return s.length()>0?s.toString():"No recordings yet";}}
}
