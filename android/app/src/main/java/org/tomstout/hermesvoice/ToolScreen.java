package org.tomstout.hermesvoice;
import android.app.*;import android.content.*;import android.content.res.Configuration;
import android.graphics.Insets;import android.graphics.Typeface;import android.graphics.drawable.GradientDrawable;
import android.os.*;import android.view.*;import android.widget.*;
import java.util.concurrent.*;
abstract class ToolScreen extends Activity {
    final ExecutorService work=Executors.newSingleThreadExecutor();final Handler ui=new Handler(Looper.getMainLooper());
    LinearLayout page;int ink,muted,surface,background,accent;boolean visible;
    void setup(String title,String subtitle){
        boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        ink=dark?0xfff1f5f9:0xff142523;muted=dark?0xffb3c4c0:0xff526660;surface=dark?0xff20332e:0xffffffff;background=dark?0xff10211c:0xfff0f5f2;accent=dark?0xff86dfb0:0xff176444;
        getWindow().setDecorFitsSystemWindows(false);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(background);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(20),dp(12),dp(20),dp(28));scroll.addView(page);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        button(page,"\u2039 Back",this::finish);text(page,title,26,true);text(page,subtitle,14,false);
    }
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    TextView text(LinearLayout box,String value,int size,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextColor(bold?ink:muted);t.setTextSize(size);if(bold)t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(7),0,dp(7));box.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
    Button button(LinearLayout box,String title,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextColor(accent);GradientDrawable shape=new GradientDrawable();shape.setColor(background);shape.setCornerRadius(dp(12));shape.setStroke(dp(1),accent);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x3317a575),shape,null));b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.topMargin=dp(7);layout.bottomMargin=dp(5);box.addView(b,layout);return b;}
    LinearLayout card(LinearLayout parent){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(12),dp(16),dp(12));GradientDrawable bg=new GradientDrawable();bg.setColor(surface);bg.setCornerRadius(dp(16));box.setBackground(bg);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(14);parent.addView(box,p);return box;}
    void message(String s){if(!isDestroyed())Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    void async(Runnable task){if(!work.isShutdown())work.submit(()->{try{task.run();}catch(Exception e){ui.post(()->message("Operation failed. Your remaining recordings are retained."));}});}
    void command(String name,byte[] payload){
        if(name.equals("clear_partials")&&!RelayService.isRunning()){async(()->{try{int count=PhoneQueueActions.clearPartials(this);ui.post(()->message("Deleted "+count+" unfinished transfer(s)."));}catch(Exception e){ui.post(()->message("Could not clear all unfinished transfers."));}});return;}
        try{RelayService.action(this,name,payload);}catch(RuntimeException e){message("Open the main screen and start the relay first.");}}
    android.content.SharedPreferences diagnostic(){return getSharedPreferences("diagnostics",MODE_PRIVATE);}
    java.util.List<RecorderInventory.Entry> catalog(){
        if(!RelayService.isConnected()||System.currentTimeMillis()-diagnostic().getLong("inventory_at",0)>15000)throw new IllegalStateException("Connect the recorder and refresh its queue first.");
        return RecorderInventory.parse(android.util.Base64.decode(diagnostic().getString("inventory",""),android.util.Base64.DEFAULT));
    }
    void confirmRecorderDelete(java.util.List<RecorderInventory.Entry> entries){
        if(entries.isEmpty()){message("The recorder queue is empty.");return;}
        StringBuilder detail=new StringBuilder("Permanently delete these "+entries.size()+" recorder recording(s)? Phone and server copies are unchanged.\n");
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();for(RecorderInventory.Entry e:entries){detail.append("\nSlot ").append(e.slot()+1).append(e.damaged()?" \u00b7 damaged":" \u00b7 "+e.durationMs()/1000.0+" seconds").append(" \u00b7 ").append(e.id().substring(0,8));out.writeBytes(e.deleteCommand());}
        new AlertDialog.Builder(this).setTitle("Delete recorder recordings?").setMessage(detail).setNegativeButton("Keep recordings",null).setPositiveButton("Delete",(d,w)->command("delete_recordings",out.toByteArray())).show();
    }
    @Override protected void onResume(){super.onResume();visible=true;}
    @Override protected void onPause(){visible=false;super.onPause();}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);work.shutdownNow();super.onDestroy();}
}
