package com.maxassistant.app.automation;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

/** User-enabled on-device accessibility. No lock bypass, system permission interaction or screen upload. */
public final class MaxAccessibilityService extends AccessibilityService {
    private static volatile MaxAccessibilityService active;
    private static volatile String lastExternalPackage;
    private DeviceController controller;

    @Override protected void onServiceConnected() { active = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName() != null) {
            String name = event.getPackageName().toString();
            if (!getPackageName().equals(name) && !systemPackage(name)) {
                try (AppWindow window = appWindow(name)) { if (window != null) lastExternalPackage = name; }
            }
        }
        DeviceController running = controller;
        if (running != null) running.onEvent();
    }
    @Override public void onInterrupt() { if (controller != null) controller.disconnected(); }
    @Override public void onDestroy() {
        if (controller != null) { controller.disconnected(); controller = null; }
        if (active == this) active = null;
        super.onDestroy();
    }
    public static boolean isConnected() { return active != null; }
    public static boolean isEnabled(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName wanted = new ComponentName(context, MaxAccessibilityService.class);
        if (enabled == null) return false;
        for (String item : enabled.split(":")) if (wanted.equals(ComponentName.unflattenFromString(item))) return true;
        return false;
    }
    public static boolean deviceUnlocked(Context context) {
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(KEYGUARD_SERVICE);
        PowerManager power = (PowerManager) context.getSystemService(POWER_SERVICE);
        return keyguard != null && !keyguard.isKeyguardLocked() && !keyguard.isDeviceLocked() && power != null && power.isInteractive();
    }
    public static String targetPackage() { return lastExternalPackage; }

    static boolean startTask(AutomationService.Session task, AutomationService owner) {
        MaxAccessibilityService service = active;
        if (service == null || service.controller != null || !deviceUnlocked(service)) return false;
        DeviceController controller = new DeviceController(service, task, owner);
        service.controller = controller;
        try { controller.start(); return true; }
        catch (RuntimeException error) { controller.stopSilently(); service.controller = null; return false; }
    }
    static void cancelTask(String sessionId) {
        MaxAccessibilityService service = active;
        if (service == null || service.controller == null) return;
        if (sessionId != null && !sessionId.equals(service.controller.id())) return;
        service.controller.stopSilently(); service.controller = null;
    }
    static final class AppWindow implements AutoCloseable {
        final AccessibilityNodeInfo root;
        final int id;
        AppWindow(AccessibilityNodeInfo root, int id) { this.root = root; this.id = id; }
        @Override public void close() { root.recycle(); }
    }
    AppWindow appWindow(String expectedPackage) {
        if (expectedPackage == null || systemPackage(expectedPackage) || !deviceUnlocked(this)) return null;
        List<AccessibilityWindowInfo> windows = getWindows();
        AppWindow result = null;
        try {
            for (AccessibilityWindowInfo window : windows) {
                // The MAX accessibility overlay is deliberately non-focusable and cannot become a target.
                if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isActive() || !window.isFocused()) continue;
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                if (root.getPackageName() != null && expectedPackage.contentEquals(root.getPackageName())) {
                    if (result != null) { root.recycle(); result.close(); return null; }
                    result = new AppWindow(root, window.getId());
                } else root.recycle();
            }
            return result;
        } finally { for (AccessibilityWindowInfo window : windows) window.recycle(); }
    }
    private static boolean systemPackage(String name) {
        return name == null || "android".equals(name) || "com.android.systemui".equals(name)
            || name.contains("permissioncontroller") || name.equals("com.android.settings") || name.equals("com.google.android.settings");
    }

    public static String snapshot() { return snapshot(lastExternalPackage); }
    static String snapshot(String expectedPackage) {
        MaxAccessibilityService service = active;
        if (service == null) return "فعّل خدمة إمكانية الوصول من إعدادات Android أولًا.";
        if (AutomationService.isRunning()) return "أكمل مهمة الهاتف الحالية أو ألغها قبل قراءة شاشة أخرى.";
        if (!deviceUnlocked(service)) return "افتح قفل الهاتف وأبقِ الشاشة مضاءة لقراءة الشاشة.";
        try (AppWindow window = service.appWindow(expectedPackage)) {
            if (window == null) return "تغيّر التطبيق أو لا توجد نافذة تطبيق نشطة ومركّزة؛ أُلغيت قراءة الشاشة.";
            StringBuilder text = new StringBuilder(); Queue<AccessibilityNodeInfo> nodes = new ArrayDeque<>();
            nodes.add(AccessibilityNodeInfo.obtain(window.root)); int count = 0;
            try {
                while (!nodes.isEmpty() && count++ < 350 && text.length() < 8000) {
                    AccessibilityNodeInfo node = nodes.remove();
                    try {
                        if (node.isPassword() || !node.isVisibleToUser()) continue;
                        CharSequence value = node.getText();
                        if (value == null || value.length() == 0) value = node.getContentDescription();
                        if (value != null && value.length() > 0) text.append(value).append('\n');
                        for (int i = 0; i < node.getChildCount() && nodes.size() < 700; i++) { AccessibilityNodeInfo child = node.getChild(i); if (child != null) nodes.add(child); }
                    } finally { node.recycle(); }
                }
            } finally { while (!nodes.isEmpty()) nodes.remove().recycle(); }
            return text.length() == 0 ? "لا يتوفر نص قابل للقراءة في الشاشة الحالية." : text.substring(0, Math.min(8000, text.length()));
        }
    }

    /** Legacy single screen actions are reviewed separately and cannot race a typed task. */
    static String perform(String action, String value, String expectedPackage) {
        MaxAccessibilityService service = active;
        if (service == null) return "فعّل خدمة إمكانية الوصول أولًا.";
        if (AutomationService.isRunning()) return "أكمل مهمة الهاتف الحالية أو ألغها قبل التحكم اليدوي من MAX.";
        if (!deviceUnlocked(service)) return "الهاتف مقفل أو الشاشة مطفأة. افتحه ثم أعد الإجراء.";
        try (AppWindow window = service.appWindow(expectedPackage)) {
            if (window == null) return "تغيّر التطبيق أو النافذة النشطة؛ أُلغي الإجراء. افتح التطبيق المقصود ثم أعد المراجعة.";
            AccessibilityNodeInfo root = window.root;
            if ("back".equals(action)) return service.performGlobalAction(GLOBAL_ACTION_BACK) ? "تم الرجوع." : "تعذّر الرجوع.";
            if ("fill".equals(action)) {
                AccessibilityNodeInfo field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (field == null) return "اختر حقلًا نصيًا غير سري أولًا.";
                try {
                    if (!field.isEditable() || field.isPassword() || !field.isVisibleToUser() || !field.isEnabled()) return "اختر حقلًا نصيًا غير سري في التطبيق المطلوب أولًا.";
                    Bundle args = new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
                    return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) ? "تمت تعبئة الحقل. راجع النص قبل الإرسال." : "هذا الحقل لا يدعم التعبئة.";
                } finally { field.recycle(); }
            }
            if ("click".equals(action) && finalAction(value)) return "استخدم طلب نشر فيسبوك أو رسالة واتساب لمراجعة المستلم والنص والتأكيد النهائي.";
            Queue<AccessibilityNodeInfo> nodes = new ArrayDeque<>(); nodes.add(AccessibilityNodeInfo.obtain(root)); int visited = 0;
            try {
                while (!nodes.isEmpty() && visited++ < 500) {
                    AccessibilityNodeInfo node = nodes.remove();
                    try {
                        if (node.isPassword() || !node.isVisibleToUser()) continue;
                        int direction = "up".equalsIgnoreCase(value) || "أعلى".equals(value) ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
                        if ("scroll".equals(action) && node.isScrollable() && node.performAction(direction)) return "تم التمرير.";
                        if ("click".equals(action) && matches(node, value)) {
                            AccessibilityNodeInfo clickable = AccessibilityNodeInfo.obtain(node); int depth = 0;
                            while (clickable != null && !clickable.isClickable() && depth++ < 3) { AccessibilityNodeInfo parent = clickable.getParent(); clickable.recycle(); clickable = parent; }
                            if (clickable != null) {
                                try { if (clickable.isEnabled() && clickable.isVisibleToUser() && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "تم الضغط على: " + value; }
                                finally { clickable.recycle(); }
                            }
                        }
                        for (int i = 0; i < node.getChildCount() && nodes.size() < 700; i++) { AccessibilityNodeInfo child = node.getChild(i); if (child != null) nodes.add(child); }
                    } finally { node.recycle(); }
                }
            } finally { while (!nodes.isEmpty()) nodes.remove().recycle(); }
            return "لم أعثر على عنصر مطابق متاح لهذا الإجراء.";
        }
    }
    private static boolean finalAction(String value) {
        String normalized = AppRegistry.normalize(value);
        return "send".equals(normalized) || "post".equals(normalized) || "share now".equals(normalized)
            || "ارسال".equals(normalized) || "نشر".equals(normalized) || "مشاركة الان".equals(normalized);
    }
    private static boolean matches(AccessibilityNodeInfo node, String value) {
        if (value == null || value.trim().isEmpty()) return false;
        CharSequence text = node.getText(), description = node.getContentDescription();
        return (text != null && text.toString().trim().equalsIgnoreCase(value.trim())) || (description != null && description.toString().trim().equalsIgnoreCase(value.trim()));
    }
}
