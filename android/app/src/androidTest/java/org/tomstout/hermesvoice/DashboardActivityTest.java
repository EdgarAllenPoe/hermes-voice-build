package org.tomstout.hermesvoice;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class DashboardActivityTest {
    Context context;Instrumentation instrumentation;MainActivity activity;
    @Before public void before()throws Exception{
        instrumentation=InstrumentationRegistry.getInstrumentation();context=instrumentation.getTargetContext();
        assertTrue(context.getPackageName().endsWith(".ci"));
        context.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("diagnostics",Context.MODE_PRIVATE).edit().clear().commit();
        context.deleteDatabase("voice_queue.db");
        shell("settings put system font_scale 1.0");
        context.getSystemService(UiModeManager.class).setApplicationNightMode(UiModeManager.MODE_NIGHT_NO);
        SystemClock.sleep(500);
    }
    @After public void after()throws Exception{
        if(activity!=null)instrumentation.runOnMainSync(()->activity.finish());
        instrumentation.waitForIdleSync();
        shell("settings put system font_scale 1.0");
        context.getSystemService(UiModeManager.class).setApplicationNightMode(UiModeManager.MODE_NIGHT_NO);
        context.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("diagnostics",Context.MODE_PRIVATE).edit().clear().commit();
    }
    private void shell(String command)throws Exception{
        try(ParcelFileDescriptor result=instrumentation.getUiAutomation().executeShellCommand(command);
            InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(result)){in.readAllBytes();}
    }
    private void launch(){
        activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();SystemClock.sleep(1000);
    }
    private View tagged(String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
    private void screenshot(String name)throws Exception{
        instrumentation.waitForIdleSync();SystemClock.sleep(300);
        Bitmap bitmap=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        File folder=context.getExternalFilesDir("ui-screenshots");assertNotNull(folder);assertTrue(folder.isDirectory()||folder.mkdirs());
        try(OutputStream out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
        bitmap.recycle();
        // Gradle uninstalls the test app afterward, deleting its private external files.
        // Copy synthetic screenshots to the disposable emulator's shared Downloads first.
        shell("mkdir -p /sdcard/Download/hermes-ui-screenshots");
        shell("cp "+new File(folder,name+".png").getAbsolutePath()+" /sdcard/Download/hermes-ui-screenshots/"+name+".png");
    }
    @Test public void tabsAndLightLayoutAreUsable()throws Exception{
        launch();
        instrumentation.runOnMainSync(()->{
            assertTrue(tagged("status_tab").isSelected());
            float density=context.getResources().getDisplayMetrics().density;
            assertTrue(tagged("status_tab").getHeight()>=48*density);
        });
        screenshot("status-light");
        instrumentation.runOnMainSync(()->tagged("diagnostics_tab").performClick());
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(()->assertTrue(tagged("diagnostics_tab").isSelected()));
        screenshot("diagnostics-light");
        instrumentation.runOnMainSync(()->((ScrollView)tagged("diagnostics_page")).fullScroll(View.FOCUS_DOWN));
        screenshot("diagnostics-details-light");
    }
    @Test public void darkLayoutUsesDarkTheme()throws Exception{
        context.getSystemService(UiModeManager.class).setApplicationNightMode(UiModeManager.MODE_NIGHT_YES);
        SystemClock.sleep(500);launch();
        instrumentation.runOnMainSync(()->assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_YES,
            activity.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK));
        screenshot("status-dark");
        instrumentation.runOnMainSync(()->tagged("diagnostics_tab").performClick());screenshot("diagnostics-dark");
    }
    @Test public void largeTextStaysScrollable()throws Exception{
        shell("settings put system font_scale 2.0");SystemClock.sleep(700);launch();
        screenshot("status-large-text");
        instrumentation.runOnMainSync(()->{
            ScrollView page=(ScrollView)tagged("status_page");
            assertTrue("Large content scrolls vertically",page.getChildAt(0).getHeight()>page.getHeight());
            assertEquals(page.getWidth(),page.getChildAt(0).getWidth());
            page.fullScroll(View.FOCUS_DOWN);
        });
        screenshot("status-large-text-actions");
        instrumentation.runOnMainSync(()->assertTrue(tagged("relay_toggle").isShown()));
    }
    @Test public void credentialsNeverAppearInDiagnostics(){
        context.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putString("token","SYNTHETIC_PRIVATE_TOKEN")
            .putString("endpoint","http://100.100.100.100:8765/v1/voice").commit();
        String text=Settings.diagnostics(context);
        assertFalse(text.contains("SYNTHETIC_PRIVATE_TOKEN"));assertFalse(text.contains("100.100.100.100"));
    }
}
