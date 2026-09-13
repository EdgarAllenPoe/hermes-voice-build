package org.tomstout.hermesvoice;
import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;
public final class UploadJob extends JobService {
    private ExecutorService executor;
    static void schedule(Context c) {
        if(!Settings.enabled(c))return;
        c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(9041,new ComponentName(c,UploadJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60*1000L).setPersisted(true).build());
    }
    @Override public boolean onStartJob(JobParameters p){executor=Executors.newSingleThreadExecutor();executor.submit(()->{Uploader.drain(this);jobFinished(p,false);executor.shutdown();});return true;}
    @Override public boolean onStopJob(JobParameters p){if(executor!=null)executor.shutdownNow();return true;}
    @Override public void onDestroy(){if(executor!=null)executor.shutdownNow();super.onDestroy();}
}
