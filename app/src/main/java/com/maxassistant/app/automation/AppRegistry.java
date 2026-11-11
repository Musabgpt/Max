package com.maxassistant.app.automation;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Resolves installed launcher labels entirely on-device; the app inventory never leaves this class. */
public final class AppRegistry {
    private AppRegistry() { }
    public static final class App {
        public final String packageName, label;
        public final Intent launchIntent;
        App(String packageName, String label, Intent launchIntent) { this.packageName = packageName; this.label = label; this.launchIntent = launchIntent; }
    }
    public static App resolve(Context context, String name) {
        String wanted = normalize(name);
        if (wanted.isEmpty()) throw new IllegalArgumentException("اكتب اسم التطبيق المثبت.");
        Map<String, App> installed = new LinkedHashMap<>();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo item : context.getPackageManager().queryIntentActivities(query, 0)) {
            if (item.activityInfo == null || !item.activityInfo.enabled) continue;
            String pkg = item.activityInfo.packageName;
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) installed.put(pkg, new App(pkg, item.loadLabel(context.getPackageManager()).toString(), launch));
        }
        String[] aliases = aliases(wanted);
        if (aliases != null) for (String pkg : aliases) if (installed.containsKey(pkg)) return installed.get(pkg);
        if (installed.containsKey(name.trim())) return installed.get(name.trim());
        List<App> matches = new ArrayList<>();
        for (App app : installed.values()) if (normalize(app.label).equals(wanted)) matches.add(app);
        if (matches.size() == 1) return matches.get(0);
        if (matches.size() > 1) throw new IllegalArgumentException("يوجد أكثر من تطبيق بهذا الاسم. اذكر اسم الحزمة لتحديد التطبيق.");
        throw new IllegalArgumentException("لم أجد تطبيقًا مثبتًا باسم «" + name.trim() + "». افتحه أو ثبّته أولًا ثم أعد الطلب.");
    }
    private static String[] aliases(String name) {
        switch (name.replace(" ", "")) {
            case "facebook": case "فيسبوك": case "الفيسبوك":
                return new String[] {"com.facebook.katana", "com.facebook.lite"};
            case "facebooklite": case "فيسبوكلايت":
                return new String[] {"com.facebook.lite"};
            case "whatsapp": case "واتساب": case "الواتساب": case "واتسابمسنجر":
                return new String[] {"com.whatsapp", "com.whatsapp.w4b"};
            case "whatsappbusiness": case "واتساباعمال": case "واتسابللاعمال":
                return new String[] {"com.whatsapp.w4b"};
            case "gmail": case "جيميل": return new String[] {"com.google.android.gm"};
            case "chrome": case "كروم": return new String[] {"com.android.chrome"};
            case "outlook": case "اوتلوك": return new String[] {"com.microsoft.office.outlook"};
            default: return null;
        }
    }
    static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
            .replaceAll("[\\u064B-\\u065F\\u0670\\u0640]", "").replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replaceAll("\\s+", " ").trim();
    }
}
