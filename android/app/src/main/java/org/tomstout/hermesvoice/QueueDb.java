package org.tomstout.hermesvoice;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
/** FULL synchronous commit precedes every recorder ACK. Migrations retain audio. */
final class QueueDb extends SQLiteOpenHelper {
    QueueDb(Context c){
        super(c,"voice_queue.db",2,new SQLiteDatabase.OpenParams.Builder()
            .addOpenFlags(SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING)
            .setSynchronousMode("FULL").build());
    }
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE messages(id TEXT PRIMARY KEY,sha TEXT NOT NULL,audio BLOB,state TEXT NOT NULL,created INTEGER NOT NULL,error TEXT,attempts INTEGER NOT NULL DEFAULT 0,next_try INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX queue_due ON messages(state,next_try,created)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){
        if(old==1&&next==2){
            db.execSQL("ALTER TABLE messages ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE messages ADD COLUMN next_try INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE INDEX queue_due ON messages(state,next_try,created)");
        }else throw new IllegalStateException("Unsupported queue migration; audio preserved");
    }
    synchronized boolean accept(byte[] audio){
        String id=Wire.validate(audio),hash=Wire.hash(audio);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            try(Cursor r=db.rawQuery("SELECT sha FROM messages WHERE id=?",new String[]{id})){
                if(r.moveToFirst()){
                    if(!hash.equals(r.getString(0)))throw new IllegalArgumentException("Message ID collision");
                    db.setTransactionSuccessful();return false;
                }
            }
            try(Cursor r=db.rawQuery("SELECT COALESCE(SUM(length(audio)),0) FROM messages",null)){
                r.moveToFirst();if(r.getLong(0)+audio.length>100L*1024*1024)
                    throw new IllegalStateException("Phone queue is full; recording stays on device");
            }
            ContentValues v=new ContentValues();v.put("id",id);v.put("sha",hash);v.put("audio",audio);
            v.put("state","pending");v.put("created",System.currentTimeMillis());
            db.insertOrThrow("messages",null,v);db.setTransactionSuccessful();return true;
        }finally{db.endTransaction();}
    }
    record Item(String id,String sha,byte[] audio,int attempts){}
    synchronized Item next(){return next(System.currentTimeMillis());}
    synchronized Item next(long now){
        try(Cursor r=getReadableDatabase().rawQuery("SELECT id,sha,audio,attempts FROM messages WHERE state='pending' AND next_try<=? ORDER BY created,id LIMIT 1",new String[]{Long.toString(now)})){
            return r.moveToFirst()?new Item(r.getString(0),r.getString(1),r.getBlob(2),r.getInt(3)):null;
        }
    }
    synchronized void sent(String id){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            ContentValues v=new ContentValues();v.put("state","sent");v.putNull("audio");v.putNull("error");
            db.update("messages",v,"id=?",new String[]{id});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    synchronized void failed(Item item,String code,boolean hold){
        ContentValues v=new ContentValues();v.put("error",code);v.put("attempts",item.attempts()+1);
        v.put("state",hold?"held":"pending");
        v.put("next_try",System.currentTimeMillis()+UploadPolicy.delayMillis(item.attempts()+1));
        getWritableDatabase().update("messages",v,"id=?",new String[]{item.id()});
    }
    synchronized void retryPending(){getWritableDatabase().execSQL("UPDATE messages SET next_try=0 WHERE state='pending'");}
    synchronized void retryHeld(){
        getWritableDatabase().execSQL("UPDATE messages SET state='pending',next_try=0 WHERE state='held'");
    }
    synchronized String summary(){
        StringBuilder s=new StringBuilder();
        try(Cursor r=getReadableDatabase().rawQuery("SELECT state,COUNT(*) FROM messages GROUP BY state",null)){
            while(r.moveToNext())s.append(r.getString(0)).append(": ").append(r.getInt(1)).append("  ");
        }
        try(Cursor r=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(length(audio)),0) FROM messages",null)){
            r.moveToFirst();s.append("\nPhone audio: ").append(r.getLong(0)).append(" / 104857600 bytes");
        }
        try(Cursor r=getReadableDatabase().rawQuery("SELECT error FROM messages WHERE error IS NOT NULL ORDER BY created DESC LIMIT 1",null)){
            if(r.moveToFirst())s.append("\nQueue problem: ").append(r.getString(0));
        }
        return s.toString();
    }
}
