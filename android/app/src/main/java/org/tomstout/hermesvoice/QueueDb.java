package org.tomstout.hermesvoice;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
/** FULL synchronous commit precedes every recorder ACK. Migrations retain audio. */
final class QueueDb extends SQLiteOpenHelper {
    private final Context context;
    QueueDb(Context c){
        super(c,"voice_queue.db",3,new SQLiteDatabase.OpenParams.Builder()
            .addOpenFlags(SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING)
            .setSynchronousMode(SQLiteDatabase.SYNC_MODE_FULL).build());context=c.getApplicationContext();
    }
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE messages(id TEXT PRIMARY KEY,sha TEXT NOT NULL,audio BLOB,state TEXT NOT NULL,created INTEGER NOT NULL,error TEXT,attempts INTEGER NOT NULL DEFAULT 0,next_try INTEGER NOT NULL DEFAULT 0,duration_ms INTEGER NOT NULL DEFAULT 0,received INTEGER NOT NULL DEFAULT 0,sent_at INTEGER NOT NULL DEFAULT 0,retain_until INTEGER NOT NULL DEFAULT 0,server_state TEXT,checked_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX queue_due ON messages(state,next_try,created)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){
        if(old<1||next!=3)throw new IllegalStateException("Unsupported queue migration; audio preserved");
        if(old==1){db.execSQL("ALTER TABLE messages ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE messages ADD COLUMN next_try INTEGER NOT NULL DEFAULT 0");db.execSQL("CREATE INDEX queue_due ON messages(state,next_try,created)");}
        db.execSQL("ALTER TABLE messages ADD COLUMN duration_ms INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE messages ADD COLUMN received INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE messages ADD COLUMN sent_at INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE messages ADD COLUMN retain_until INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE messages ADD COLUMN server_state TEXT");
        db.execSQL("ALTER TABLE messages ADD COLUMN checked_at INTEGER NOT NULL DEFAULT 0");
    }
    synchronized boolean accept(byte[] audio){
        String id=Wire.validate(audio),hash=Wire.hash(audio);purgeExpired();
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            try(Cursor r=db.rawQuery("SELECT sha,state FROM messages WHERE id=?",new String[]{id})){
                if(r.moveToFirst()){
                    if(!r.getString(0).isEmpty()&&!hash.equals(r.getString(0)))throw new IllegalArgumentException("Message ID collision");
                    if(!r.getString(1).equals("recorder")){db.setTransactionSuccessful();return false;}
                }
            }
            try(Cursor r=db.rawQuery("SELECT COALESCE(SUM(length(audio)),0) FROM messages",null)){
                r.moveToFirst();if(r.getLong(0)+audio.length>100L*1024*1024)
                    throw new IllegalStateException("Phone queue is full; recording stays on device");
            }
            ContentValues v=new ContentValues();v.put("id",id);v.put("sha",hash);v.put("audio",audio);
            v.put("state","pending");v.put("duration_ms",Wire.le32(audio,12)/16);v.put("received",System.currentTimeMillis());
            if(db.update("messages",v,"id=? AND state='recorder'",new String[]{id})==0){v.put("created",System.currentTimeMillis());db.insertOrThrow("messages",null,v);}db.setTransactionSuccessful();return true;
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
            ContentValues v=new ContentValues();v.put("state","sent");v.putNull("error");long now=System.currentTimeMillis();
            v.put("sent_at",now);v.put("server_state","received");
            if(Settings.prefs(context).getBoolean("keep_audio",false)){v.put("retain_until",now+86_400_000L);RetentionJob.schedule(context);}else v.putNull("audio");
            db.update("messages",v,"id=? AND state='pending'",new String[]{id});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    synchronized void failed(Item item,String code,boolean hold){
        ContentValues v=new ContentValues();v.put("error",code);v.put("attempts",item.attempts()+1);
        v.put("state",hold?"held":"pending");
        v.put("next_try",System.currentTimeMillis()+UploadPolicy.delayMillis(item.attempts()+1));
        getWritableDatabase().update("messages",v,"id=? AND state='pending'",new String[]{item.id()});
    }
    synchronized void retryPending(){getWritableDatabase().execSQL("UPDATE messages SET next_try=0 WHERE state='pending'");}
    synchronized void retryHeld(){
        getWritableDatabase().execSQL("UPDATE messages SET state='pending',next_try=0 WHERE state='held'");
    }
    record Stats(int pending,int held,int sent) {}
    synchronized Stats stats(){
        int pending=0,held=0,sent=0;
        try(Cursor r=getReadableDatabase().rawQuery("SELECT state,COUNT(*) FROM messages GROUP BY state",null)){
            while(r.moveToNext()){
                switch(r.getString(0)){
                    case "pending" -> pending=r.getInt(1);
                    case "held" -> held=r.getInt(1);
                    case "sent" -> sent=r.getInt(1);
                }
            }
        }
        return new Stats(pending,held,sent);
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
    record History(String id,String state,long created,int durationMs,boolean playable,String serverState,long received,long sentAt,long checkedAt,String error) {
        String label(){return switch(state){case "recorder"->"On recorder (last seen)";case "pending"->"Saved on phone \u00b7 waiting to send";case "held"->"Saved on phone \u00b7 held for review";case "discarded"->"Deleted locally";case "sent"->switch(serverState==null?"":serverState){case "done"->"Processed by Hermes";case "review"->"Transcribed \u00b7 awaiting review";case "transcribing"->"Server is transcribing";case "ready","delivering"->"Hermes processing";case "failed","uncertain"->"Server processing needs attention";case "rejected"->"Rejected on server";default->"Received by server";};default->"Status unknown";};}
    }
    synchronized java.util.List<History> history(){
        purgeExpired();java.util.List<History> out=new java.util.ArrayList<>();
        try(Cursor q=getReadableDatabase().rawQuery("SELECT id,state,created,duration_ms,audio IS NOT NULL,server_state,received,sent_at,checked_at,error FROM messages ORDER BY created DESC LIMIT 200",null)){
            while(q.moveToNext())out.add(new History(q.getString(0),q.getString(1),q.getLong(2),q.getInt(3),q.getInt(4)!=0,q.getString(5),q.getLong(6),q.getLong(7),q.getLong(8),q.getString(9)));
        }return out;
    }
    synchronized void observed(java.util.List<RecorderInventory.Entry> entries){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            java.util.Set<String> present=new java.util.HashSet<>();for(RecorderInventory.Entry e:entries)if(!e.damaged())present.add(e.id());
            try(Cursor old=db.rawQuery("SELECT id FROM messages WHERE state='recorder'",null)){
                java.util.List<String> gone=new java.util.ArrayList<>();while(old.moveToNext())if(!present.contains(old.getString(0)))gone.add(old.getString(0));
                for(String id:gone)db.delete("messages","id=? AND state='recorder'",new String[]{id});
            }
            for(RecorderInventory.Entry e:entries)if(!e.damaged()){
                ContentValues v=new ContentValues();v.put("id",e.id());v.put("sha","");v.put("state","recorder");v.put("created",System.currentTimeMillis());v.put("duration_ms",e.durationMs());
                db.insertWithOnConflict("messages",null,v,SQLiteDatabase.CONFLICT_IGNORE);
            }db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    synchronized byte[] playback(String id){purgeExpired();try(Cursor q=getReadableDatabase().rawQuery("SELECT audio FROM messages WHERE id=?",new String[]{id})){return q.moveToFirst()?q.getBlob(0):null;}}
    synchronized void purgeExpired(){
        if(Settings.prefs(context).getBoolean("keep_audio",false))getWritableDatabase().execSQL("UPDATE messages SET audio=NULL,retain_until=0 WHERE state='sent' AND retain_until<=?",new Object[]{System.currentTimeMillis()});
        else getWritableDatabase().execSQL("UPDATE messages SET audio=NULL,retain_until=0 WHERE state='sent'");
    }
    synchronized void discard(String id){
        ContentValues v=new ContentValues();v.put("state","discarded");v.putNull("audio");v.putNull("error");v.put("retain_until",0);
        getWritableDatabase().update("messages",v,"id=? AND state IN ('pending','held','recorder')",new String[]{id});
    }
    synchronized void retry(String id){ContentValues v=new ContentValues();v.put("state","pending");v.put("next_try",0);v.putNull("error");getWritableDatabase().update("messages",v,"id=? AND state IN ('held','pending')",new String[]{id});}
    synchronized java.util.List<String[]> statusCandidates(){
        java.util.List<String[]> out=new java.util.ArrayList<>();try(Cursor q=getReadableDatabase().rawQuery("SELECT id,sha FROM messages WHERE state='sent' AND COALESCE(server_state,'') NOT IN ('done','rejected') AND checked_at<? ORDER BY checked_at,created DESC LIMIT 20",new String[]{Long.toString(System.currentTimeMillis()-60_000)})){while(q.moveToNext())out.add(new String[]{q.getString(0),q.getString(1)});}return out;
    }
    synchronized void serverStatus(String id,String state){ContentValues v=new ContentValues();v.put("server_state",state);v.put("checked_at",System.currentTimeMillis());getWritableDatabase().update("messages",v,"id=? AND state='sent'",new String[]{id});}
}
