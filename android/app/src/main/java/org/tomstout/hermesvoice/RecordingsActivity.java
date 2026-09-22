package org.tomstout.hermesvoice;
import android.app.*;import android.os.*;import android.widget.*;
import java.util.*;
public final class RecordingsActivity extends ToolScreen {
    private LinearLayout rows;private String signature="";private final AudioPlayback player=new AudioPlayback();private boolean loading;
    private final Runnable pulse=new Runnable(){public void run(){if(!visible)return;load();ui.postDelayed(this,2000);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);setup("Recordings","Follow each recording from the recorder to Hermes. The most recent 200 entries are shown.");
        LinearLayout options=card(page);button(options,"Refresh delivery status",()->async(()->{ProcessingStatus.refresh(this);ui.post(this::load);}));
        button(options,"Stop playback",player::close);
        Switch keep=new Switch(this);keep.setText("Keep delivered audio for 24 hours");keep.setTextColor(ink);keep.setMinHeight(dp(52));keep.setChecked(Settings.prefs(this).getBoolean("keep_audio",false));options.addView(keep);
        keep.setOnCheckedChangeListener((v,on)->{Settings.prefs(this).edit().putBoolean("keep_audio",on).apply();RetentionJob.schedule(this);async(()->{try(QueueDb db=new QueueDb(this)){db.purgeExpired();}ui.post(this::load);});});
        text(options,"Off by default. Turning this off removes retained delivered audio; receipts remain. Older deleted audio cannot be recovered.",12,false);
        rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);page.addView(rows);
    }
    @Override protected void onResume(){super.onResume();ui.post(pulse);async(()->ProcessingStatus.refresh(this));}
    @Override protected void onPause(){ui.removeCallbacks(pulse);player.close();super.onPause();}
    private void load(){if(loading||isDestroyed())return;loading=true;async(()->{try(QueueDb db=new QueueDb(this)){List<QueueDb.History> list=db.history();ui.post(()->{loading=false;if(isDestroyed())return;String key=list.toString();if(key.equals(signature))return;signature=key;rows.removeAllViews();if(list.isEmpty())text(rows,"No recordings yet. Tap your recorder button to begin.",16,false);for(QueueDb.History item:list)draw(item);});}finally{ui.post(()->loading=false);}});}
    private void draw(QueueDb.History item){LinearLayout box=card(rows);text(box,item.label(),17,true);
        text(box,(item.received()>0?"Received on phone: ":"First seen: ")+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(new Date(item.received()>0?item.received():item.created())),13,false);
        text(box,(item.durationMs()>0?String.format(Locale.ROOT,"%.1f seconds",item.durationMs()/1000.0):"Duration unavailable for older receipt")+" \u00b7 "+item.id().substring(0,8),13,false);
        if(item.sentAt()>0)text(box,"Server receipt verified. "+(item.checkedAt()>0?"Processing checked "+DashboardState.ago(System.currentTimeMillis(),item.checkedAt()):"Processing has not been verified yet."),12,false);
        if(item.error()!=null)text(box,item.error(),13,false);
        if(item.playable())button(box,"\u25b6 Play recording",()->async(()->{try(QueueDb db=new QueueDb(this)){byte[] audio=db.playback(item.id());if(audio!=null&&visible)player.play(audio);else ui.post(()->message("This recording no longer has a local audio copy."));}}));
        if(item.state().equals("pending")||item.state().equals("held")){
            button(box,"Retry delivery",()->async(()->{try(QueueDb db=new QueueDb(this)){db.retry(item.id());}Uploader.drain(this);ui.post(this::load);}));
            button(box,"Delete unsent phone copy",()->new AlertDialog.Builder(this).setTitle("Delete unsent recording?").setMessage("Delete the phone copy of "+item.id().substring(0,8)+"? An upload already in progress may finish first. This cannot recall messages received by the server.").setNegativeButton("Keep",null).setPositiveButton("Delete",(d,w)->async(()->{PhoneQueueActions.discard(this,item.id());ui.post(this::load);})).show());
        }
        if(item.state().equals("recorder"))button(box,"Delete from recorder",()->{try{List<RecorderInventory.Entry> selected=new ArrayList<>();for(RecorderInventory.Entry e:catalog())if(e.id().equals(item.id()))selected.add(e);confirmRecorderDelete(selected);}catch(Exception e){message("Reconnect and refresh the recorder queue first.");}});
    }
}
