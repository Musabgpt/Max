package com.maxassistant.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import com.maxassistant.app.MainActivity;
import com.maxassistant.app.ai.AiRepository;
import com.maxassistant.app.data.AppStore;
import java.util.concurrent.atomic.AtomicBoolean;

/** Scheduled prompts generate text only; no lockscreen interaction, sends, or purchases. */
final class TaskRunner {
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    static void runDue(Context context) {
        if (!RUNNING.compareAndSet(false, true)) return;
        AppStore store = new AppStore(context);
        try {
            store.recoverRunningTasks();
            if (!store.prefs().getBoolean("bg_enabled", false) || !"work".equals(store.prefs().getString("work_mode", "work"))) return;
            int count = 0;
            for (AppStore.Task task : store.getDueTasks(System.currentTimeMillis())) {
                if (Thread.currentThread().isInterrupted() || count++ >= 3) break;
                if (!store.prefs().getBoolean("bg_enabled", false) || !"work".equals(store.prefs().getString("work_mode", "work"))) break;
                store.setTaskStatus(task.id, "running", "");
                try {
                    String result = new AiRepository(context).reply(task.prompt);
                    store.setTaskStatus(task.id, "completed", result);
                    notifyResult(context, task.id, task.title, "اكتمل تجهيز النتيجة. افتح التطبيق لمراجعتها.");
                } catch (Exception | LinkageError error) {
                    String message = error.getMessage();
                    store.setTaskStatus(task.id, "failed", message == null ? "تعذّر تنفيذ المهمة. تحقق من النموذج والاتصال." : message);
                    notifyResult(context, task.id, task.title, "تحتاج المهمة إلى مراجعتك داخل التطبيق.");
                }
            }
        } finally { try { store.close(); } finally { RUNNING.set(false); } }
    }

    static void notifyResult(Context context, long id, String title, String text) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel("task_results", "نتائج المهام", NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, (int) id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, "task_results")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title)
            .setContentText(text).setContentIntent(pending).setAutoCancel(true).build();
        try { manager.notify((int) (1000 + id % 100000), notification); } catch (SecurityException ignored) { }
    }
}
