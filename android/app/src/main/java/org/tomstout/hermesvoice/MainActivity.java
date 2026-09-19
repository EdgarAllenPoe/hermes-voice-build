package org.tomstout.hermesvoice;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.companion.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Two focused screens: everyday health and explicit setup/troubleshooting controls. */
public final class MainActivity extends Activity {
    private final ExecutorService io=Executors.newFixedThreadPool(2);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final AtomicBoolean reading=new AtomicBoolean(),checking=new AtomicBoolean();
    private boolean visible;
    private int selectedTab;
    private long lastAutoCheck;
    private Palette colors;
    private Button statusTab,diagnosticsTab,checkButton,diagnosticCheck,relayButton,reviewButton,retryButton;
    private ScrollView statusPage,diagnosticsPage;
    private TextView heroTitle,heroDetail,heroLabel,heroSymbol,relayLabel,receiptLabel,diagnosticText,updatedLabel;
    private LinearLayout hero;
    private HealthCard recorderCard,deliveryCard,serverCard;
    private DashboardReader.Snapshot latest;
    private String lastDiagnostic="";
    private final Runnable pulse=new Runnable(){public void run(){
        if(!visible)return;refresh();
        if(SystemClock.elapsedRealtime()-lastAutoCheck>=60_000&&Settings.prefs(MainActivity.this).contains("token"))
            checkConnection();
        ui.postDelayed(this,2000);
    }};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        colors=new Palette((getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
        getWindow().setDecorFitsSystemWindows(false);
        LinearLayout outer=column();outer.setBackgroundColor(colors.background);
        FrameLayout frame=new FrameLayout(this);frame.addView(outer,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER_HORIZONTAL));
        frame.setBackgroundColor(colors.background);setContentView(frame);
        frame.setOnApplyWindowInsetsListener((v,insets)->{
            WindowInsetsController controller=getWindow().getInsetsController();
            int flags=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            if(controller!=null)controller.setSystemBarsAppearance(colors.dark?0:flags,flags);
            Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
            frame.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        // Keep text comfortably readable in a tablet or desktop-sized window.
        frame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)outer.getLayoutParams();
            int width=Math.min(r-l-frame.getPaddingLeft()-frame.getPaddingRight(),dp(680));
            if(width>0&&lp.width!=width){lp.width=width;outer.setLayoutParams(lp);}
        });
        LinearLayout heading=column();heading.setPadding(dp(22),dp(20),dp(22),dp(14));
        TextView brand=label(BuildConfig.CI_BUILD?"HERMES VOICE \u00b7 PREVIEW":"HERMES VOICE",12,colors.accent,true);
        brand.setLetterSpacing(.13f);heading.addView(brand);
        TextView title=label("Your voice, connected.",27,colors.text,true);title.setAccessibilityHeading(true);
        add(heading,title,6);outer.addView(heading);
        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(4),dp(4),dp(4),dp(4));
        tabs.setBackground(shape(colors.track,0,14));
        statusTab=tab("Status",0);diagnosticsTab=tab("Diagnostics",1);
        tabs.addView(statusTab,new LinearLayout.LayoutParams(0,-2,1));tabs.addView(diagnosticsTab,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams tabLayout=new LinearLayout.LayoutParams(-1,-2);tabLayout.setMargins(dp(20),0,dp(20),dp(8));outer.addView(tabs,tabLayout);
        FrameLayout pages=new FrameLayout(this);outer.addView(pages,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout home=page();statusPage=scroll(home);statusPage.setTag("status_page");pages.addView(statusPage);
        LinearLayout diagnostics=page();diagnosticsPage=scroll(diagnostics);diagnosticsPage.setTag("diagnostics_page");pages.addView(diagnosticsPage);
        buildStatus(home);buildDiagnostics(diagnostics);
        selectTab(state==null?0:state.getInt("tab",0));
        refresh();
    }
    private void buildStatus(LinearLayout page){
        LinearLayout relayRow=new LinearLayout(this);relayRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView eyebrow=label("AT A GLANCE",11,colors.muted,true);eyebrow.setLetterSpacing(.1f);
        relayRow.addView(eyebrow,new LinearLayout.LayoutParams(0,-2,1));
        relayLabel=label("Checking relay\u2026",12,colors.muted,false);relayRow.addView(relayLabel);page.addView(relayRow);
        hero=column();hero.setPadding(dp(20),dp(20),dp(20),dp(20));add(page,hero,14);
        LinearLayout heroTop=new LinearLayout(this);heroTop.setGravity(Gravity.CENTER_VERTICAL);
        heroSymbol=label("\u2022",24,colors.amber,true);heroSymbol.setGravity(Gravity.CENTER);
        heroSymbol.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        heroTop.addView(heroSymbol,new LinearLayout.LayoutParams(dp(44),dp(44)));
        heroLabel=label("CHECKING STATUS",11,colors.amber,true);heroLabel.setLetterSpacing(.08f);
        LinearLayout.LayoutParams hl=new LinearLayout.LayoutParams(-2,-2);hl.setMarginStart(dp(12));heroTop.addView(heroLabel,hl);hero.addView(heroTop);
        heroTitle=label("Getting things ready",26,colors.text,true);heroTitle.setAccessibilityHeading(true);add(hero,heroTitle,14);
        heroDetail=label("Reading your phone and recorder status\u2026",15,colors.muted,false);add(hero,heroDetail,8);
        recorderCard=new HealthCard("Recorder");add(page,recorderCard.box,14);
        deliveryCard=new HealthCard("Delivery");add(page,deliveryCard.box,10);
        serverCard=new HealthCard("Server");add(page,serverCard.box,10);
        checkButton=button("Check connection",true,this::checkConnection);checkButton.setTag("check_connection");add(page,checkButton,18);
        relayButton=button("Start relay",false,this::toggleRelay);relayButton.setTag("relay_toggle");add(page,relayButton,8);
        receiptLabel=label("Server receipts confirm delivery. Hermes replies arrive separately in Telegram.",12,colors.muted,false);add(page,receiptLabel,14);
        TextView tip=label("Double-press the recorder button to speak. A short pause finishes your recording.",13,colors.muted,false);add(page,tip,10);
        updatedLabel=label("",11,colors.muted,false);add(page,updatedLabel,16);
    }
    private void buildDiagnostics(LinearLayout page){
        TextView title=label("Diagnostics",23,colors.text,true);title.setAccessibilityHeading(true);page.addView(title);
        add(page,label("Setup, connection checks and details when you need them.",14,colors.muted,false),6);
        LinearLayout setup=section(page,"Setup & connections");
        add(setup,button("Server settings",false,this::serverSettings),8);
        add(setup,button("Pair recorder",false,this::pair),8);
        add(setup,button("App permissions",false,this::permissionSettings),8);
        LinearLayout delivery=section(page,"Delivery tools");
        diagnosticCheck=button("Check connection",false,this::checkConnection);add(delivery,diagnosticCheck,8);
        retryButton=button("Retry waiting uploads",false,()->runQueueAction(false));add(delivery,retryButton,8);
        reviewButton=button("Review held recordings",false,this::reviewHeld);add(delivery,reviewButton,8);
        add(delivery,label("Retrying keeps the same recording IDs, so successful receipts are not duplicated.",12,colors.muted,false),10);
        LinearLayout details=section(page,"Technical details");
        add(details,button("Refresh details",false,this::refresh),8);
        diagnosticText=label("Reading diagnostics\u2026",13,colors.text,false);diagnosticText.setTypeface(Typeface.MONOSPACE);
        diagnosticText.setTextIsSelectable(true);diagnosticText.setTag("diagnostic_details");add(details,diagnosticText,16);
        add(page,button("Export diagnostics",false,this::exportDiagnostics),16);
        add(page,label("The export excludes your token, server address, transcripts and audio.",12,colors.muted,false),8);
        add(page,label("Hermes Voice "+BuildConfig.VERSION_NAME+" \u00b7 "+(BuildConfig.DEBUG?"test build":"release build"),12,colors.muted,false),20);
    }
    private void selectTab(int index){
        selectedTab=index==1?1:0;
        statusPage.setVisibility(selectedTab==0?View.VISIBLE:View.GONE);
        diagnosticsPage.setVisibility(selectedTab==1?View.VISIBLE:View.GONE);
        styleTab(statusTab,selectedTab==0);styleTab(diagnosticsTab,selectedTab==1);
    }
    private Button tab(String title,int index){
        Button b=button(title,false,()->selectTab(index));b.setTag(index==0?"status_tab":"diagnostics_tab");return b;
    }
    private void styleTab(Button b,boolean selected){
        b.setSelected(selected);b.setTextColor(selected?colors.text:colors.muted);
        b.setBackground(ripple(selected?colors.surface:colors.track,0,10));b.setStateDescription(selected?"Selected tab":"Tab");
    }
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("tab",selectedTab);super.onSaveInstanceState(out);}
    @Override protected void onResume(){super.onResume();visible=true;ui.post(pulse);}
    @Override protected void onPause(){visible=false;ui.removeCallbacks(pulse);super.onPause();}
    @Override protected void onDestroy(){visible=false;ui.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
    private void refresh(){
        if(io.isShutdown()||!reading.compareAndSet(false,true))return;
        io.submit(()->{
            try{
                DashboardReader.Snapshot snapshot=DashboardReader.read(this,checking.get());
                runOnUiThread(()->{if(!isDestroyed()){latest=snapshot;render(snapshot);}});
            }finally{reading.set(false);}
        });
    }
    private void render(DashboardReader.Snapshot snapshot){
        DashboardState.Input s=snapshot.input();DashboardState state=new DashboardState(s);
        int tone=colors.tone(state.overall.level()),tint=colors.tint(state.overall.level());
        hero.setBackground(shape(tint,colors.border,20));heroSymbol.setText(symbol(state.overall.level()));
        heroSymbol.setTextColor(tone);heroSymbol.setBackground(shape(colors.surface,0,22));
        heroLabel.setText(state.overall.level()==DashboardState.Level.GOOD?"READY":state.overall.level()==DashboardState.Level.ERROR?"ACTION NEEDED":"STATUS CHECK");
        heroLabel.setTextColor(tone);setText(heroTitle,state.overall.title());setText(heroDetail,state.overall.detail());
        recorderCard.render(state.recorder);deliveryCard.render(state.delivery);serverCard.render(state.server);
        setText(relayLabel,!s.relayEnabled?"Relay paused":s.relayRunning?"Relay active":"Relay not running");
        relayLabel.setTextColor(s.relayEnabled&&s.relayRunning?colors.green:colors.muted);
        checkButton.setEnabled(!s.checking);diagnosticCheck.setEnabled(!s.checking);
        checkButton.setAlpha(s.checking?.65f:1f);setText(checkButton,s.checking?"Checking connection\u2026":"Check connection");
        setText(diagnosticCheck,s.checking?"Checking connection\u2026":"Check connection");
        setText(relayButton,s.relayEnabled&&s.relayRunning?"Pause relay":"Start relay");
        reviewButton.setEnabled(s.held>0);reviewButton.setAlpha(s.held>0?1f:.5f);
        retryButton.setEnabled(s.pending>0&&s.relayEnabled);retryButton.setAlpha(s.pending>0&&s.relayEnabled?1f:.5f);
        setText(updatedLabel,"Updates automatically while this screen is open.");
        if(!snapshot.diagnostics().equals(lastDiagnostic)){lastDiagnostic=snapshot.diagnostics();diagnosticText.setText(lastDiagnostic);}
    }
    private void checkConnection(){
        if(io.isShutdown()||!checking.compareAndSet(false,true))return;
        lastAutoCheck=SystemClock.elapsedRealtime();refresh();
        io.submit(()->{
            try{ServerHealth.check(this);}
            finally{checking.set(false);refresh();}
        });
    }
    private void toggleRelay(){
        if(Settings.enabled(this)&&RelayService.isRunning()){
            new AlertDialog.Builder(this).setTitle("Pause relay?")
                .setMessage("Your phone will stop receiving and uploading recordings. Saved recordings stay in their queues.")
                .setNegativeButton("Keep running",null).setPositiveButton("Pause", (d,w)->{
                    Settings.prefs(this).edit().putBoolean("enabled",false).apply();
                    stopService(new Intent(this,RelayService.class));
                    getSystemService(android.app.job.JobScheduler.class).cancel(9041);refresh();
                }).show();
        }else startRelay();
    }
    private void startRelay(){
        if(!permissions()){permissionSettings();return;}
        io.submit(()->{
            try{
                Endpoint.parse(Settings.endpoint(this));Settings.token(this);
                if(Settings.prefs(this).getInt("association",-1)<0)throw new IllegalStateException("Pair your recorder in Diagnostics first.");
                Settings.prefs(this).edit().putBoolean("enabled",true).commit();
                runOnUiThread(()->{if(!isDestroyed()){observe(this);RelayService.start(this);UploadJob.schedule(this);refresh();}});
            }catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){selectTab(1);toast("Check recorder pairing and server settings before starting.");}});}
        });
    }
    private void runQueueAction(boolean held){
        if(!Settings.enabled(this)){toast("Start the relay before retrying delivery.");return;}
        io.submit(()->{
            try(QueueDb db=new QueueDb(this)){if(held)db.retryHeld();else db.retryPending();}
            catch(Exception e){runOnUiThread(()->toast("Phone storage needs attention. Recordings are retained."));return;}
            Uploader.drain(this);refresh();
        });
    }
    private void reviewHeld(){
        int count=latest==null?0:latest.input().held;
        new AlertDialog.Builder(this).setTitle(count+" held recording"+(count==1?"":"s"))
            .setMessage("The server rejected these recordings. They remain on your phone. Correct the server problem before retrying; no audio is deleted.")
            .setNegativeButton("Keep held",null).setPositiveButton("Retry held",(d,w)->runQueueAction(true)).show();
    }
    private void serverSettings(){
        LinearLayout content=column();content.setPadding(dp(22),dp(10),dp(22),dp(12));
        content.addView(label("Server address",13,colors.text,true));
        EditText url=new EditText(this);url.setSingleLine(true);url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(Settings.endpoint(this));url.setMinHeight(dp(52));content.addView(url);
        add(content,label("Access token",13,colors.text,true),16);
        EditText token=new EditText(this);token.setSingleLine(true);token.setMinHeight(dp(52));
        token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING|EditorInfo.IME_ACTION_DONE);
        token.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);token.setSaveEnabled(false);
        token.setHint(Settings.prefs(this).contains("token")?"Saved \u00b7 leave blank to keep":"Paste your server token");content.addView(token);
        add(content,label("Your saved token stays hidden. Changes take effect for the next connection check.",12,colors.muted,false),12);
        ScrollView scroller=new ScrollView(this);scroller.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Server settings").setView(scroller)
            .setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String address=url.getText().toString(),secret=token.getText().toString().trim();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            io.submit(()->{
                try{
                    Settings.saveServer(this,address,secret);
                    runOnUiThread(()->{if(!isDestroyed()){token.setText("");dialog.dismiss();toast("Server settings saved.");checkConnection();refresh();}});
                }catch(Exception e){
                    runOnUiThread(()->{if(!isDestroyed()){
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        toast(e instanceof IllegalArgumentException?e.getMessage():"Could not save settings. Check the address and token.");
                    }});
                }
            });
        }));dialog.show();
    }
    private void exportDiagnostics(){
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE,"Hermes-Voice-diagnostics.txt");startActivityForResult(save,102);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==102&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            android.net.Uri destination=data.getData();
            io.submit(()->{
                try(java.io.OutputStream out=getContentResolver().openOutputStream(destination)){
                    if(out==null)throw new java.io.IOException();
                    String report=DashboardReader.read(this,false).diagnostics()+"\nNo tokens, addresses, transcripts or audio included.\n";
                    out.write(report.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    runOnUiThread(()->toast("Diagnostics exported."));
                }catch(Exception e){runOnUiThread(()->toast("Could not export diagnostics."));}
            });
        }
    }
    private boolean permissions(){return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED;}
    private void request(){Settings.prefs(this).edit().putBoolean("permissions_requested",true).apply();requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.POST_NOTIFICATIONS},100);}
    private void permissionSettings(){
        if(!permissions()||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            request();
            toast("If Android no longer shows a prompt, enable permissions in the phone\u2019s app settings.");
        }else startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);refresh();}
    private void pair(){
        if(!permissions()){permissionSettings();return;}
        BluetoothAdapter a=getSystemService(BluetoothManager.class).getAdapter();
        if(a==null||!a.isEnabled()){toast("Turn on Bluetooth, then pair again.");return;}
        toast("Hold the recorder button for two seconds, then release. Choose Hermes Voice and enter its pairing code.");
        ScanFilter scan=new ScanFilter.Builder().setServiceUuid(new ParcelUuid(Wire.SERVICE)).build();
        BluetoothLeDeviceFilter filter=new BluetoothLeDeviceFilter.Builder().setScanFilter(scan).build();
        AssociationRequest request=new AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(true).build();
        try{
            getSystemService(CompanionDeviceManager.class).associate(request,getMainExecutor(),new CompanionDeviceManager.Callback(){
                @Override public void onAssociationPending(IntentSender sender){
                    try{startIntentSenderForResult(sender,101,null,0,0,0);}
                    catch(Exception e){toast("Could not open pairing. Check Nearby devices permission.");}
                }
                @Override public void onAssociationCreated(AssociationInfo info){
                    if(info.getDeviceMacAddress()==null){toast("Recorder address unavailable. Try pairing again.");return;}
                    if(!permissions()){toast("Allow Nearby devices before pairing.");return;}
                    try{
                        String address=Settings.bluetoothAddress(info.getDeviceMacAddress().toString());
                        BluetoothDevice dev=a.getRemoteDevice(address);
                        Settings.prefs(MainActivity.this).edit().putInt("association",info.getId()).putString("address",address).commit();
                        if(dev.getBondState()!=BluetoothDevice.BOND_BONDED)dev.createBond();
                        observe(MainActivity.this);toast("Complete the Bluetooth pairing prompt, then start the relay.");refresh();
                    }catch(SecurityException e){toast("Nearby devices permission was revoked. Allow it in app settings.");}
                    catch(Exception e){toast("Pairing did not finish. Check Bluetooth and try again.");}
                }
                @Override public void onFailure(CharSequence error){toast("No recorder selected. Hold its button for two seconds and try again.");}
            });
        }catch(Exception e){toast("Pairing is unavailable. Check Nearby devices permission.");}
    }
    static void observe(Context c){
        int id=Settings.prefs(c).getInt("association",-1);if(id<0)return;
        try{
            String address=Settings.recorderAddress(c);if(address==null)return;
            CompanionDeviceManager m=c.getSystemService(CompanionDeviceManager.class);
            if(Build.VERSION.SDK_INT>=36)m.startObservingDevicePresence(new ObservingDevicePresenceRequest.Builder().setAssociationId(id).build());
            else m.startObservingDevicePresence(address);
        }catch(SecurityException e){Settings.status(c,"Companion permission needs attention");}
        catch(Exception e){Settings.status(c,"Companion observation needs attention");}
    }
    private void toast(String text){if(!isDestroyed())Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout page(){LinearLayout p=column();p.setPadding(dp(20),dp(18),dp(20),dp(24));return p;}
    private ScrollView scroll(LinearLayout child){ScrollView v=new ScrollView(this);v.setFillViewport(true);v.setClipToPadding(false);v.addView(child);return v;}
    private LinearLayout section(LinearLayout page,String title){
        LinearLayout box=column();box.setPadding(dp(18),dp(18),dp(18),dp(18));box.setBackground(shape(colors.surface,colors.border,18));
        TextView heading=label(title,17,colors.text,true);heading.setAccessibilityHeading(true);box.addView(heading);add(page,box,16);return box;
    }
    private TextView label(String text,float size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");
        t.setIncludeFontPadding(false);t.setLineSpacing(dp(3),1);
        t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return t;
    }
    private Button button(String text,boolean primary,Runnable action){
        Button b=new Button(this);b.setText(text);b.setTextSize(15);b.setAllCaps(false);b.setMinHeight(dp(52));
        b.setMinimumHeight(dp(52));b.setMinWidth(0);b.setPadding(dp(14),dp(12),dp(14),dp(12));
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setTextColor(primary?colors.onAccent:colors.text);b.setStateListAnimator(null);
        b.setBackground(ripple(primary?colors.accent:colors.surface,primary?0:colors.border,13));
        b.setOnClickListener(v->action.run());return b;
    }
    private void add(LinearLayout parent,View child,int top){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(child,p);
    }
    private GradientDrawable shape(int fill,int border,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));
        if(border!=0)d.setStroke(dp(1),border);return d;
    }
    private RippleDrawable ripple(int fill,int border,int radius){
        return new RippleDrawable(ColorStateList.valueOf(colors.dark?0x22FFFFFF:0x18000000),shape(fill,border,radius),null);
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private static void setText(TextView view,String text){if(!view.getText().toString().equals(text))view.setText(text);}
    private static String symbol(DashboardState.Level l){return l==DashboardState.Level.GOOD?"\u2713":l==DashboardState.Level.ERROR?"!":"\u2022";}
    private final class HealthCard {
        final LinearLayout box=column();final TextView badge,title,detail;
        HealthCard(String category){
            box.setPadding(dp(18),dp(16),dp(18),dp(16));box.setBackground(shape(colors.surface,colors.border,18));
            LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name=label(category,13,colors.muted,true);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            badge=label("\u2022 Checking",11,colors.amber,true);badge.setPadding(dp(9),dp(5),dp(9),dp(5));row.addView(badge);box.addView(row);
            title=label("Checking\u2026",19,colors.text,true);add(box,title,10);
            detail=label("",13,colors.muted,false);add(box,detail,6);
        }
        void render(DashboardState.Status status){
            String level=status.level()==DashboardState.Level.GOOD?"Good":status.level()==DashboardState.Level.ERROR?"Action":"Waiting";
            setText(badge,symbol(status.level())+" "+level);badge.setTextColor(colors.tone(status.level()));badge.setBackground(shape(colors.tint(status.level()),0,10));
            setText(title,status.title());setText(detail,status.detail());
        }
    }
    private static final class Palette {
        final boolean dark;final int background,surface,track,text,muted,border,accent,onAccent,green,amber,red,greenTint,amberTint,redTint;
        Palette(boolean d){
            dark=d;background=c(d?"#10151F":"#F4F6FA");surface=c(d?"#1B2330":"#FFFFFF");track=c(d?"#222D3D":"#E5EAF2");
            text=c(d?"#F0F4FC":"#17243B");muted=c(d?"#BAC6DA":"#52627B");border=c(d?"#303F55":"#DCE3ED");
            accent=c(d?"#AFC6FF":"#2856C5");onAccent=c(d?"#14274F":"#FFFFFF");
            green=c(d?"#8BE3B9":"#146142");amber=c(d?"#F5D185":"#77520B");red=c(d?"#FFB4AB":"#A12732");
            greenTint=c(d?"#1A352E":"#EAF7F0");amberTint=c(d?"#373023":"#FFF6DF");redTint=c(d?"#3D252B":"#FFF0F0");
        }
        int tone(DashboardState.Level l){return l==DashboardState.Level.GOOD?green:l==DashboardState.Level.ERROR?red:amber;}
        int tint(DashboardState.Level l){return l==DashboardState.Level.GOOD?greenTint:l==DashboardState.Level.ERROR?redTint:amberTint;}
        static int c(String x){return Color.parseColor(x);}
    }
}
