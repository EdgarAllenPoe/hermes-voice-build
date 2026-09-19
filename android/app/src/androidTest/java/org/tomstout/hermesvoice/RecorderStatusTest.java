package org.tomstout.hermesvoice;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class RecorderStatusTest {
    private Context context;
    private SharedPreferences diagnostics;
    @Before public void before(){
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Use the isolated CI package",target.getPackageName().endsWith(".ci"));
        context=new ContextWrapper(target){
            @Override public SharedPreferences getSharedPreferences(String name,int mode){
                return super.getSharedPreferences("recorder-status-regression-"+name,mode);
            }
        };
        diagnostics=context.getSharedPreferences("diagnostics",Context.MODE_PRIVATE);
        diagnostics.edit().clear().commit();
    }
    @After public void after(){if(diagnostics!=null)diagnostics.edit().clear().commit();}

    @Test public void oldAppSnapshotIsExplicitlyHistorical(){
        diagnostics.edit().putString("recorder","Recorder queue: 1\nCapture active: false").commit();
        String text=Settings.diagnostics(context);
        assertTrue(text.contains("Recorder queue: 1"));
        assertTrue(text.contains("unknown (older app snapshot)"));
        assertTrue(text.contains("last known; updated when connected"));
    }
    @Test public void finalConfirmedTransferPersistsZeroWithoutASecondInfoRead(){
        RecorderStatus status=new RecorderStatus();
        status.update(new RecorderInfo(new byte[]{1,0,1,0,0,0,0,0}));
        Settings.recorderSnapshot(context,status.text());
        long readAt=diagnostics.getLong("recorder_read_at",0);
        assertTrue(readAt>0);
        status.acknowledged();
        Settings.metric(context,"recorder",status.text());
        Settings.metric(context,"connection","Waiting for recorder");
        String text=Settings.diagnostics(context);
        assertTrue(text.contains("Recorder queue: 0 (estimated after confirmed transfer)"));
        assertTrue(text.contains("last known; updated when connected"));
        assertEquals(readAt,diagnostics.getLong("recorder_read_at",0));
    }
    @Test public void newSnapshotReplacesEstimateAndLegacyStatus(){
        Settings.metric(context,"recorder","Recorder queue: 15");
        RecorderStatus status=new RecorderStatus();
        status.update(new RecorderInfo(new byte[]{1,0,0,0,0,0,0,0}));
        Settings.recorderSnapshot(context,status.text());
        String text=Settings.diagnostics(context);
        assertTrue(text.contains("Recorder queue: 0\n"));
        assertFalse(text.contains("estimated after"));
        assertFalse(text.contains("older app snapshot"));
        assertTrue(diagnostics.getLong("recorder_read_at",0)>0);
    }
    @Test public void missingStatusDoesNotInventAQueue(){
        assertTrue(Settings.diagnostics(context).contains("recorder: not available"));
        assertFalse(Settings.diagnostics(context).contains("Recorder queue: 0"));
    }
}
