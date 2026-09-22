package org.tomstout.hermesvoice;
import android.app.job.*;import android.content.*;
/** Independent of network/relay so paused uploads do not indefinitely retain delivered audio. */
public final class RetentionJob extends JobService {
    static void schedule(Context c){JobScheduler jobs=c.getSystemService(JobScheduler.class);
        if(!Settings.prefs(c).getBoolean("keep_audio",false)){jobs.cancel(9042);return;}
        jobs.schedule(new JobInfo.Builder(9042,new ComponentName(c,RetentionJob.class)).setPeriodic(3_600_000).setPersisted(true).build());
    }
    @Override public boolean onStartJob(JobParameters params){new Thread(()->{try(QueueDb db=new QueueDb(this)){db.purgeExpired();}finally{jobFinished(params,false);}},"HermesAudioExpiry").start();return true;}
    @Override public boolean onStopJob(JobParameters params){return true;}
}
