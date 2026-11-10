package com.maxassistant.app.service;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.widget.Toast;
import com.maxassistant.app.data.AppStore;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Captures only newly visible notifications from the explicitly supported messaging apps. */
public final class InboxService extends NotificationListenerService {
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
        "com.whatsapp", "com.whatsapp.w4b", "com.google.android.gm", "com.microsoft.office.outlook"));
    private static final ConcurrentHashMap<String, ReplyHandle> REPLIES = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> seen = new ConcurrentHashMap<>();
    private AppStore store;

    @Override public void onCreate() { super.onCreate(); store = new AppStore(this); }
    public static boolean enabled(Context context) {
        String granted = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        String target = new ComponentName(context, InboxService.class).flattenToString();
        if (granted == null) return false;
        for (String value : granted.split(":")) if (target.equalsIgnoreCase(value)) return true;
        return false;
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !ALLOWED.contains(sbn.getPackageName())) return;
        Notification notification = sbn.getNotification();
        if (notification == null || (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        Bundle extras = notification.extras;
        if (extras == null) return;
        String title = string(extras.getCharSequence(Notification.EXTRA_TITLE));
        CharSequence textValue = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (textValue == null || textValue.length() == 0) textValue = extras.getCharSequence(Notification.EXTRA_TEXT);
        String text = string(textValue);
        if (text.isEmpty() && title.isEmpty()) return;
        String digest = title + "\n" + text;
        if (!digest.equals(seen.put(sbn.getKey(), digest))) store.addInbox(sbn.getPackageName(), title, text, sbn.getKey());
        REPLIES.remove(sbn.getKey());
        if (notification.actions == null) return;
        for (Notification.Action action : notification.actions) {
            RemoteInput[] inputs = action.getRemoteInputs();
            if (action.actionIntent == null || inputs == null) continue;
            for (RemoteInput input : inputs) {
                if (input.getAllowFreeFormInput()) {
                    REPLIES.put(sbn.getKey(), new ReplyHandle(action.actionIntent, inputs, input.getResultKey(), digest));
                    return;
                }
            }
        }
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null) { REPLIES.remove(sbn.getKey()); seen.remove(sbn.getKey()); }
    }
    @Override public void onListenerDisconnected() { REPLIES.clear(); seen.clear(); }
    @Override public void onDestroy() { REPLIES.clear(); seen.clear(); if (store != null) store.close(); super.onDestroy(); }
    public static boolean canReply(String key) { return key != null && REPLIES.containsKey(key); }

    public static void replyReviewed(Activity activity, String key, String text) {
        ReplyHandle handle = key == null ? null : REPLIES.get(key);
        if (handle == null) { toast(activity, "لم يعد رد الإشعار متاحًا. افتح المحادثة في تطبيقها."); return; }
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty() || clean.length() > 10000) { toast(activity, "أدخل ردًا من 1 إلى 10000 حرف."); return; }
        new AlertDialog.Builder(activity).setTitle("مراجعة الرد قبل إرساله")
            .setMessage("الإشعار:\n" + handle.description + "\n\nالرد:\n" + clean)
            .setNegativeButton("إلغاء", null).setPositiveButton("إرسال الرد", (dialog, which) -> {
                if (REPLIES.get(key) != handle) { toast(activity, "تغيّر الإشعار. راجع الرسالة الجديدة ثم أعد الرد."); return; }
                try {
                    Bundle result = new Bundle(); result.putCharSequence(handle.resultKey, clean);
                    Intent fill = new Intent(); RemoteInput.addResultsToIntent(handle.inputs, fill, result);
                    handle.intent.send(activity, 0, fill);
                    REPLIES.remove(key, handle);
                    toast(activity, "سُلّم الرد إلى تطبيق الرسائل. تحقق من حالة إرساله داخل المحادثة.");
                } catch (PendingIntent.CanceledException | SecurityException error) {
                    REPLIES.remove(key, handle); toast(activity, "انتهت صلاحية الإشعار. افتح المحادثة لإرسال الرد.");
                }
            }).show();
    }

    private static String string(CharSequence text) {
        if (text == null) return "";
        String value = text.toString(); return value.length() > 12000 ? value.substring(0, 12000) : value;
    }
    private static void toast(Context context, String message) { Toast.makeText(context, message, Toast.LENGTH_LONG).show(); }
    private static final class ReplyHandle {
        final PendingIntent intent; final RemoteInput[] inputs; final String resultKey, description;
        ReplyHandle(PendingIntent intent, RemoteInput[] inputs, String resultKey, String description) {
            this.intent = intent; this.inputs = inputs; this.resultKey = resultKey; this.description = description;
        }
    }
}
