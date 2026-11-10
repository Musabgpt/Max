package com.maxassistant.app.automation;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.Queue;

/** User-enabled accessibility tools. Never opens a lock screen or reads password nodes. */
public final class MaxAccessibilityService extends AccessibilityService {
    private static volatile MaxAccessibilityService active;
    private static volatile String lastExternalPackage;
    @Override protected void onServiceConnected() { active = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName() != null) {
            String name = event.getPackageName().toString();
            if (!getPackageName().equals(name) && !"com.android.systemui".equals(name) && !"android".equals(name)) lastExternalPackage = name;
        }
    }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() { if (active == this) active = null; super.onDestroy(); }

    public static boolean isEnabled(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String wanted = new ComponentName(context, MaxAccessibilityService.class).flattenToString();
        if (enabled == null) return false;
        for (String item : enabled.split(":")) if (wanted.equalsIgnoreCase(item)) return true;
        return false;
    }

    public static String targetPackage() { return lastExternalPackage; }

    private boolean locked() {
        KeyguardManager manager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return manager != null && manager.isKeyguardLocked();
    }

    public static String snapshot() {
        MaxAccessibilityService service = active;
        if (service == null) return "فعّل خدمة إمكانية الوصول من الإعدادات أولًا.";
        if (service.locked()) return "افتح قفل الهاتف لقراءة الشاشة. يستمر توليد النصوص والمهام المحلية في الخلفية حسب قيود Android.";
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return "لا توجد شاشة متاحة للقراءة الآن.";
        StringBuilder text = new StringBuilder();
        Queue<AccessibilityNodeInfo> nodes = new ArrayDeque<>();
        nodes.add(root);
        int count = 0;
        while (!nodes.isEmpty() && count++ < 180 && text.length() < 8000) {
            AccessibilityNodeInfo node = nodes.remove();
            if (!node.isPassword() && node.isVisibleToUser()) {
                CharSequence value = node.getText();
                if (value == null || value.length() == 0) value = node.getContentDescription();
                if (value != null && value.length() > 0) text.append(value).append('\n');
                for (int i = 0; i < node.getChildCount(); i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) nodes.add(child);
                }
            }
        }
        return text.length() == 0 ? "لا يتوفر نص قابل للقراءة في الشاشة الحالية." : text.toString();
    }

    static String snapshot(String expectedPackage) {
        MaxAccessibilityService service = active;
        if (service == null) return "فعّل خدمة إمكانية الوصول أولًا.";
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null || expectedPackage == null || root.getPackageName() == null || !expectedPackage.contentEquals(root.getPackageName())) return "تغيّر التطبيق المفتوح؛ أُلغيت قراءة الشاشة.";
        return snapshot();
    }

    /** Only call after ActionExecutor's visible review dialog is accepted. */
    static String perform(String action, String value, String expectedPackage) {
        MaxAccessibilityService service = active;
        if (service == null) return "فعّل خدمة إمكانية الوصول أولًا.";
        if (service.locked()) return "الهاتف مقفل. افتح القفل ثم أعد الإجراء.";
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return "لا تتوفر نافذة قابلة للتحكم.";
        if (expectedPackage == null || root.getPackageName() == null || !expectedPackage.contentEquals(root.getPackageName())) return "تغيّر التطبيق المفتوح؛ أُلغي الإجراء. افتح التطبيق المقصود ثم أعد المراجعة.";
        if ("back".equals(action)) return service.performGlobalAction(GLOBAL_ACTION_BACK) ? "تم الرجوع." : "تعذّر الرجوع.";
        if ("fill".equals(action)) {
            AccessibilityNodeInfo field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (field == null || !field.isEditable() || field.isPassword()) return "اختر حقلًا نصيًا غير سري في التطبيق المطلوب أولًا.";
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
            return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) ? "تمت تعبئة الحقل. راجع النص قبل الإرسال." : "هذا الحقل لا يدعم التعبئة.";
        }
        Queue<AccessibilityNodeInfo> nodes = new ArrayDeque<>();
        nodes.add(root);
        int visited = 0;
        while (!nodes.isEmpty() && visited++ < 350) {
            AccessibilityNodeInfo node = nodes.remove();
            if (node.isPassword() || !node.isVisibleToUser()) continue;
            int direction = "up".equalsIgnoreCase(value) || "أعلى".equals(value) ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
            if ("scroll".equals(action) && node.isScrollable() && node.performAction(direction)) return "تم التمرير.";
            if ("click".equals(action) && matches(node, value)) {
                AccessibilityNodeInfo clickable = node;
                int depth = 0;
                while (clickable != null && !clickable.isClickable() && depth++ < 3) clickable = clickable.getParent();
                if (clickable != null && clickable.isEnabled() && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "تم الضغط على: " + value;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) nodes.add(child);
            }
        }
        return "لم أعثر على عنصر مطابق متاح لهذا الإجراء.";
    }

    private static boolean matches(AccessibilityNodeInfo node, String value) {
        if (value == null || value.trim().isEmpty()) return false;
        CharSequence text = node.getText(), description = node.getContentDescription();
        return (text != null && text.toString().trim().equalsIgnoreCase(value.trim())) ||
                (description != null && description.toString().trim().equalsIgnoreCase(value.trim()));
    }
}
