package com.maxassistant.app.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** OS-managed recovery path when the persistent foreground session is reclaimed. */
public final class TaskJobService extends JobService {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Future<?> active;
    @Override public boolean onStartJob(JobParameters params) {
        active = worker.submit(() -> {
            try { TaskRunner.runDue(getApplicationContext()); }
            finally { jobFinished(params, false); TaskService.schedule(getApplicationContext()); }
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) { if (active != null) active.cancel(true); return true; }
    @Override public void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
