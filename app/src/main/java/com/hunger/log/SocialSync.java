package com.hunger.log;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

public class SocialSync extends JobService {
    private static final int JOB_ID = 406;
    private volatile boolean stopped;

    static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, SocialSync.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(30000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPersisted(true).build());
    }

    static void cancel(Context context) {
        ((JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(JOB_ID);
    }

    @Override public boolean onStartJob(JobParameters parameters) {
        stopped = false;
        SocialClient.EXECUTOR.execute(() -> {
            AppUpdates.checkIfDue(this);
            boolean retry = false;
            try {
                SocialClient client = new SocialClient(this);
                if (client.signedIn()) {
                    client.queueLatest(false);
                    client.flush();
                    client.snapshot();
                    HomeWidget.refresh(this, "HUGGY · 친구 상태 갱신", -1);
                }
            } catch (Exception error) { retry = true; }
            if (!stopped) jobFinished(parameters, retry);
        });
        return true;
    }

    @Override public boolean onStopJob(JobParameters parameters) {
        stopped = true;
        return true;
    }
}
