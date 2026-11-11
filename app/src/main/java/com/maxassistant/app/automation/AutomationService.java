package com.maxassistant.app.automation;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Toast;
import com.maxassistant.app.data.AppStore;
import java.util.UUID;

/** Visible, bounded and non-resumable device sessions. No plan is restored from an Intent or disk. */
public final class AutomationService extends Service {
    private static final String CHANNEL = "max_device_tasks";
    private static final String ACTION_START = "com.maxassistant.app.automation.START";
    private static final String ACTION_CANCEL = "com.maxassistant.app.automation.CANCEL";
    private static final int NOTIFICATION_ID = 73;
    private static volatile Session pending;
    private static volatile AutomationService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppStore store;
    private Session current;
    private boolean ended;

    static final class Session {
        final String id = UUID.randomUUID().toString();
        final DevicePlan plan;
        final String packageName, label;
        final long startedAt = SystemClock.elapsedRealtime();
        Session(DevicePlan plan, AppRegistry.App app) { this.plan = plan; packageName = app.packageName; label = app.label; }
    }

    public static boolean isRunning() { return pending != null; }

    public static void reviewAndStart(Activity activity, DevicePlan plan) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || plan == null) return;
        if (isRunning()) { toast(activity, "توجد مهمة هاتف جارية. ألغها أو انتظر انتهاءها."); return; }
        final AppRegistry.App app;
        try { app = AppRegistry.resolve(activity, plan.app); }
        catch (RuntimeException error) { toast(activity, error.getMessage()); return; }
        if (plan.kind != DevicePlan.Kind.OPEN_APP) {
            if (!MaxAccessibilityService.deviceUnlocked(activity)) { toast(activity, "افتح قفل الهاتف وأبقِ الشاشة مضاءة ثم أعد الطلب."); return; }
            if (!MaxAccessibilityService.isEnabled(activity) || !MaxAccessibilityService.isConnected()) {
                new AlertDialog.Builder(activity).setTitle("تفعيل التحكم المرئي في الهاتف")
                    .setMessage("تحتاج هذه المهمة إلى خدمة MAX لإمكانية الوصول التي تفعّلها أنت من إعدادات Android. ستقرأ عناصر التطبيق المحدد على الجهاز فقط، وتعرض إلغاءً دائمًا وتطلب تأكيدًا منفصلًا قبل الإرسال أو النشر.\n\nفعّل الخدمة ثم ارجع وأعد الطلب.")
                    .setNegativeButton("إلغاء", null).setPositiveButton("إعدادات إمكانية الوصول", (dialog, which) -> {
                        try { activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                        catch (RuntimeException error) { toast(activity, "افتح إعدادات Android ← إمكانية الوصول ← MAX يدويًا."); }
                    }).show();
                return;
            }
        }
        String preview = "التطبيق: " + app.label + "\nالحزمة: " + app.packageName;
        if (plan.kind == DevicePlan.Kind.WHATSAPP_MESSAGE) preview += "\nالمستلم: " + plan.recipient;
        if (plan.kind != DevicePlan.Kind.OPEN_APP) preview += "\n\nالنص:\n" + plan.body + "\n\nسأجهّز المسودة. سأنتظر تأكيدك على الشاشة قبل الضغط النهائي على الإرسال أو النشر. راقب التطبيق؛ يمكنك إلغاء المهمة في أي وقت.";
        new AlertDialog.Builder(activity).setTitle(plan.kind == DevicePlan.Kind.OPEN_APP ? "مراجعة فتح التطبيق" : "مراجعة مهمة الهاتف")
            .setMessage(preview).setNegativeButton("إلغاء", null)
            .setPositiveButton(plan.kind == DevicePlan.Kind.OPEN_APP ? "فتح التطبيق" : "ابدأ تجهيز المسودة", (dialog, which) -> {
                if (isRunning()) { toast(activity, "توجد مهمة هاتف جارية بالفعل."); return; }
                if (plan.kind == DevicePlan.Kind.OPEN_APP) {
                    try {
                        activity.startActivity(new Intent(app.launchIntent));
                        append(activity, "طلبت من Android فتح التطبيق: " + app.label + ". تحقّق من ظهور واجهته.");
                    } catch (RuntimeException error) { toast(activity, "تعذّر فتح التطبيق. راجع تثبيته وصلاحيات Android."); }
                    return;
                }
                if (!MaxAccessibilityService.deviceUnlocked(activity) || !MaxAccessibilityService.isConnected()) { toast(activity, "تغيّرت حالة الهاتف أو خدمة إمكانية الوصول. أعد المراجعة."); return; }
                Session session = new Session(plan, app);
                synchronized (AutomationService.class) { if (pending != null) return; pending = session; }
                try {
                    // Starting the target Activity remains a direct result of this foreground review gesture.
                    Intent launch;
                    if (plan.kind == DevicePlan.Kind.WHATSAPP_MESSAGE) launch = new Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(app.packageName).putExtra(Intent.EXTRA_TEXT, plan.body);
                    else launch = new Intent(app.launchIntent);
                    activity.startActivity(launch);
                    activity.startForegroundService(new Intent(activity, AutomationService.class).setAction(ACTION_START).putExtra("session", session.id));
                } catch (RuntimeException error) {
                    if (pending == session) pending = null;
                    append(activity, "لم تبدأ مهمة الهاتف: منع Android تشغيل التطبيق أو الجلسة المرئية. أعد الطلب من MAX.");
                    toast(activity, "تعذّر بدء المهمة. ارجع إلى MAX وأعد المراجعة.");
                }
            }).show();
    }

    public static void cancel(Context context) {
        Session target = pending;
        if (target == null) return;
        AutomationService service = instance;
        if (service != null && service.current == target && !service.ended) { service.handler.post(() -> { if (service.current == target && pending == target) service.finish("أُلغيت مهمة الهاتف. لم أنفّذ إرسالًا أو نشرًا نهائيًا."); }); return; }
        synchronized (AutomationService.class) { if (pending == target) pending = null; }
        MaxAccessibilityService.cancelTask(target.id);
        context.stopService(new Intent(context, AutomationService.class));
    }

    @Override public void onCreate() {
        super.onCreate(); instance = this; store = new AppStore(this);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "مهام الهاتف المرئية", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("جلسة قصيرة لمهمة راجعتها أنت، مع زر للإلغاء.");
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String token = intent == null ? null : intent.getStringExtra("session");
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            if (current != null && current.id.equals(token)) finish("أُلغيت مهمة الهاتف. لم أنفّذ إرسالًا أو نشرًا نهائيًا.");
            return START_NOT_STICKY;
        }
        Session session = pending;
        if (intent == null || !ACTION_START.equals(intent.getAction()) || session == null || !session.id.equals(token) || (current != null && !ended)) {
            if (current == null) stopSelf();
            return START_NOT_STICKY;
        }
        current = session; ended = false;
        try {
            Notification notification = notification();
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTIFICATION_ID, notification);
            record("بدأت تجهيز المسودة في " + session.label + ". أنتظر تأكيدًا منفصلًا قبل أي إرسال أو نشر.");
            if (!MaxAccessibilityService.startTask(session, this)) finish("لم تتصل خدمة إمكانية الوصول. أعد تفعيلها من إعدادات Android ثم أعد الطلب.");
            else handler.postDelayed(() -> { if (current == session && !ended) finish("انتهت مهلة مهمة الهاتف. راجع المسودة في التطبيق؛ لم أتابع الإرسال أو النشر."); }, CommitGuard.MAX_DURATION_MS);
        } catch (RuntimeException error) { finish("تعذّر تشغيل جلسة الهاتف المرئية. أعد الطلب من MAX بعد مراجعة إعدادات Android."); }
        return START_NOT_STICKY;
    }

    private Notification notification() {
        Intent cancel = new Intent(this, AutomationService.class).setAction(ACTION_CANCEL).putExtra("session", current.id);
        PendingIntent cancelIntent = PendingIntent.getService(this, 73, cancel, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("MAX: مهمة هاتف قيد المراجعة").setContentText("راقب الشاشة؛ يلزم تأكيد منفصل قبل الإرسال أو النشر.")
            .setVisibility(Notification.VISIBILITY_SECRET).setLocalOnly(true).setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "إلغاء المهمة", cancelIntent).build()).build();
    }

    void progress(String sessionId, String text) {
        if (current != null && !ended && current.id.equals(sessionId)) record(text);
    }
    void complete(String sessionId, String text) {
        if (current != null && !ended && current.id.equals(sessionId)) finish(text);
    }
    private void finish(String text) {
        if (ended || current == null) { if (current == null) stopSelf(); return; }
        ended = true;
        String id = current.id;
        MaxAccessibilityService.cancelTask(id);
        if (pending == current) pending = null;
        handler.removeCallbacksAndMessages(null);
        record(text);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    private void record(String text) {
        try { if (store != null) store.addMessage("assistant", text); }
        catch (RuntimeException ignored) { /* Progress remains visible in the foreground overlay if local history is unavailable. */ }
    }
    private static void append(Context context, String text) {
        try (AppStore store = new AppStore(context)) { store.addMessage("assistant", text); }
        catch (RuntimeException ignored) { }
    }
    private static void toast(Context context, String text) { Toast.makeText(context, text == null ? "تعذّر تنفيذ المهمة." : text, Toast.LENGTH_LONG).show(); }
    @Override public void onTaskRemoved(Intent rootIntent) { finish("توقفت مهمة الهاتف عند إغلاق MAX. راجع المسودة في التطبيق."); super.onTaskRemoved(rootIntent); }
    @Override public void onDestroy() {
        if (!ended && current != null) {
            ended = true; MaxAccessibilityService.cancelTask(current.id);
            if (pending == current) pending = null;
            record("انقطعت جلسة الهاتف. لن أستأنف أي إرسال أو نشر؛ راجع التطبيق وأعد الطلب عند الحاجة.");
        }
        handler.removeCallbacksAndMessages(null);
        if (instance == this) instance = null;
        if (store != null) store.close();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
