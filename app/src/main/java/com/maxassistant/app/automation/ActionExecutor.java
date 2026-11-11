package com.maxassistant.app.automation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import org.json.JSONObject;
import java.util.function.Consumer;

/** An explicit visible review is required for every device action. LLM output is never executable. */
public final class ActionExecutor {
    private ActionExecutor() { }

    public static void readScreen(Activity activity, Consumer<String> result) {
        if (AutomationService.isRunning()) { result.accept("أكمل مهمة الهاتف الحالية أو ألغها قبل قراءة شاشة أخرى."); return; }
        String target = MaxAccessibilityService.targetPackage();
        if (!MaxAccessibilityService.isEnabled(activity)) { result.accept("فعّل خدمة إمكانية الوصول من الإعدادات أولًا."); return; }
        if (target == null) { result.accept("افتح التطبيق الذي تريد قراءة شاشته ثم ارجع إلى MAX."); return; }
        new AlertDialog.Builder(activity).setTitle("مراجعة قراءة الشاشة")
            .setMessage("سأعود إلى التطبيق السابق لقراءة النص المرئي ثم أعرضه هنا.\n\nالتطبيق: " + target + "\n\nلن تُقرأ حقول كلمات المرور، ولن يُرسل النص إلى نموذج خارجي تلقائيًا.")
            .setNegativeButton("إلغاء", null).setPositiveButton("قراءة الآن", (dialog, which) -> {
                if (AutomationService.isRunning()) { result.accept("بدأت مهمة هاتف أخرى؛ أُلغيت قراءة الشاشة."); return; }
                activity.moveTaskToBack(true);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    String snapshot = MaxAccessibilityService.snapshot(target);
                    Intent reopen = new Intent(activity, activity.getClass()).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    try {
                        activity.startActivity(reopen);
                        new Handler(Looper.getMainLooper()).postDelayed(() -> { if (!activity.isFinishing() && !activity.isDestroyed()) result.accept(snapshot); }, 400);
                    } catch (RuntimeException error) { toast(activity, "افتح MAX لمراجعة الشاشة مرة أخرى؛ منع Android إعادة فتح الواجهة."); }
                }, 800);
            }).show();
    }
    public static void preview(Activity activity, String action, String value) {
        if (AutomationService.isRunning()) { toast(activity, "أكمل مهمة الهاتف الحالية أو ألغها قبل إجراء آخر."); return; }
        String clean = value == null ? "" : value.trim();
        if (clean.length() > 10000) { toast(activity, "النص طويل جدًا لهذا الإجراء."); return; }
        if ("open_app".equals(action)) {
            try { AutomationService.reviewAndStart(activity, DevicePlan.openApp(clean)); }
            catch (IllegalArgumentException error) { toast(activity, error.getMessage()); }
            return;
        }
        String label;
        switch (action) {
            case "open_url": label = "فتح الموقع"; break;
            case "open_app": label = "فتح التطبيق"; break;
            case "click": label = "الضغط على عنصر في الشاشة"; break;
            case "fill": label = "تعبئة الحقل المحدد"; break;
            case "back": label = "الرجوع في الشاشة"; break;
            case "scroll": label = "تمرير الشاشة"; break;
            case "email": label = "فتح مسودة بريد للمراجعة"; break;
            case "flight": label = "البحث عن رحلة طيران"; break;
            case "train": label = "البحث عن رحلة قطار"; break;
            default: toast(activity, "الإجراء غير مدعوم."); return;
        }
        String target = MaxAccessibilityService.targetPackage();
        boolean screen = "click".equals(action) || "fill".equals(action) || "back".equals(action) || "scroll".equals(action);
        if (screen && target == null) { toast(activity, "افتح التطبيق المطلوب أولًا ثم ارجع إلى MAX لمراجعة الإجراء."); return; }
        new AlertDialog.Builder(activity).setTitle("مراجعة الإجراء")
                .setMessage(label + (screen ? "\nالتطبيق المقصود: " + target : "") + "\n\n" + clean + "\n\nراجع المستلم والنص والمبلغ قبل الإرسال أو الشراء. يعتمد التحكم على الشاشة المفتوحة لحظة التنفيذ.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("تنفيذ الآن", (dialog, which) -> {
                    if (AutomationService.isRunning()) { toast(activity, "بدأت مهمة هاتف أخرى؛ أُلغي هذا الإجراء."); return; }
                    if (screen) {
                        activity.moveTaskToBack(true);
                        new Handler(Looper.getMainLooper()).postDelayed(() -> toast(activity, MaxAccessibilityService.perform(action, clean, target)), 700);
                    } else executeReviewed(activity, action, clean);
                }).show();
    }

    private static void executeReviewed(Activity activity, String action, String value) {
        try {
            switch (action) {
                case "open_url": BrowserActivity.open(activity, value); return;
                case "flight": BrowserActivity.open(activity, "https://www.google.com/travel/flights?q=" + Uri.encode(value)); return;
                case "train": BrowserActivity.open(activity, "https://www.google.com/search?q=" + Uri.encode("حجز قطار " + value)); return;
                case "email": {
                    // The installed email client owns authentication and final send confirmation.
                    Intent email = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
                    if (value.startsWith("{")) {
                        JSONObject message = new JSONObject(value);
                        String to = message.optString("to", "").trim();
                        if (!to.isEmpty()) {
                            if (to.contains("\r") || to.contains("\n") || !android.util.Patterns.EMAIL_ADDRESS.matcher(to).matches()) throw new IllegalArgumentException("أدخل عنوان بريد إلكتروني صالحًا دون أسطر إضافية.");
                            email.setData(Uri.parse("mailto:" + Uri.encode(to)));
                            email.putExtra(Intent.EXTRA_EMAIL, new String[] { to });
                        }
                        String subject = message.optString("subject", "");
                        if (subject.contains("\r") || subject.contains("\n")) throw new IllegalArgumentException("عنوان الرسالة يجب أن يكون سطرًا واحدًا.");
                        email.putExtra(Intent.EXTRA_SUBJECT, subject);
                        email.putExtra(Intent.EXTRA_TEXT, message.optString("body", ""));
                    } else if (android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
                        email.setData(Uri.parse("mailto:" + Uri.encode(value)));
                        email.putExtra(Intent.EXTRA_EMAIL, new String[] { value });
                    } else email.putExtra(Intent.EXTRA_TEXT, value);
                    activity.startActivity(Intent.createChooser(email, "مراجعة البريد في تطبيقك")); return;
                }
                case "open_app": {
                    activity.startActivity(AppRegistry.resolve(activity, value).launchIntent); return;
                }
                default: toast(activity, "الإجراء غير مدعوم.");
            }
        } catch (ActivityNotFoundException exception) { toast(activity, "لا يوجد تطبيق مناسب لهذا الإجراء."); }
        catch (SecurityException exception) { toast(activity, "يتطلب هذا الإجراء صلاحية يمنحها المستخدم من إعدادات Android."); }
        catch (IllegalArgumentException exception) { toast(activity, exception.getMessage()); }
        catch (org.json.JSONException exception) { toast(activity, "بيانات مسودة البريد غير صالحة."); }
    }

    private static void toast(Activity activity, String text) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show(); }
}
