package com.maxassistant.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import com.maxassistant.app.MainActivity;
import com.maxassistant.app.data.AppStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-started persistent session; Android may still defer work for battery/resource limits. */
public final class TaskService extends Service {
    private static final int JOB_ID = 5401;
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!worker.isShutdown()) worker.execute(() -> TaskRunner.runDue(getApplicationContext()));
            timer.postDelayed(this, 60_000L);
        }
    };

    public static void start(Context context) {
        try (AppStore store = new AppStore(context)) { store.prefs().edit().putBoolean("bg_enabled", true).apply(); }
        schedule(context);
        try { context.startForegroundService(new Intent(context, TaskService.class)); }
        catch (RuntimeException error) {
            TaskRunner.notifyResult(context, 0, "العمل في الخلفية", "Android أجّل الخدمة. افتح التطبيق واضغط تشغيل الخلفية مجددًا؛ بقيت المهام محفوظة.");
        }
    }

    public static void stop(Context context) {
        try (AppStore store = new AppStore(context)) { store.prefs().edit().putBoolean("bg_enabled", false).apply(); }
        context.stopService(new Intent(context, TaskService.class));
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler != null) scheduler.cancel(JOB_ID);
    }

    /** Inexact scheduling; no exact alarm permission or promise of continuous wakefulness. */
    public static void schedule(Context context) {
        try (AppStore store = new AppStore(context)) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        if (!store.prefs().getBoolean("bg_enabled", false) || !"work".equals(store.prefs().getString("work_mode", "work"))) {
            scheduler.cancel(JOB_ID); return;
        }
        long next = Long.MAX_VALUE;
        for (AppStore.Task task : store.getTasks()) {
            if ("pending".equals(task.status) || "waiting_unlock".equals(task.status)) next = Math.min(next, task.scheduledAt);
        }
        if (next == Long.MAX_VALUE) { scheduler.cancel(JOB_ID); return; }
        long delay = Math.max(1000L, next - System.currentTimeMillis());
        JobInfo.Builder info = new JobInfo.Builder(JOB_ID, new ComponentName(context, TaskJobService.class))
            .setMinimumLatency(delay).setOverrideDeadline(delay + 15 * 60_000L).setPersisted(true);
        if ("nvidia".equals(store.prefs().getString("provider", "local"))) info.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        scheduler.schedule(info.build());
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("assistant_session", "جلسة المساعد", NotificationManager.IMPORTANCE_LOW));
        Notification notification = sessionNotification();
        if (Build.VERSION.SDK_INT >= 34) startForeground(41, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(41, notification);
        timer.post(tick);
    }

    private Notification sessionNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, TaskService.class).setAction("stop"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean work;
        try (AppStore store = new AppStore(this)) { work = "work".equals(store.prefs().getString("work_mode", "work")); }
        return new Notification.Builder(this, "assistant_session")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("MAX · " + (work ? "وضع العمل" : "وضع النوم"))
            .setContentText(work ? "المهام النصية تعمل حسب قيود Android. افتح القفل للتحكم بالشاشة." : "المهام معلّقة حتى العودة إلى وضع العمل.")
            .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null, "إيقاف", stop).build()).build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "stop".equals(intent.getAction())) { stop(this); return START_NOT_STICKY; }
        try (AppStore store = new AppStore(this)) { if (!store.prefs().getBoolean("bg_enabled", false)) { stopSelf(); return START_NOT_STICKY; } }
        getSystemService(NotificationManager.class).notify(41, sessionNotification());
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { timer.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy(); }
}
