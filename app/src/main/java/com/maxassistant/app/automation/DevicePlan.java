package com.maxassistant.app.automation;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Iterator;

/** A reviewed, high-level device task. Neither model output nor screen text is executable code. */
public final class DevicePlan {
    public enum Kind { OPEN_APP, FACEBOOK_POST, WHATSAPP_MESSAGE }
    public final Kind kind;
    public final String app;
    public final String recipient;
    public final String body;

    private DevicePlan(Kind kind, String app, String recipient, String body) {
        if (kind == null) throw new IllegalArgumentException("نوع المهمة غير صالح.");
        this.kind = kind;
        this.app = value(app, "التطبيق", 160, false);
        this.recipient = value(recipient, "المستلم", 120, false);
        this.body = value(body, "النص", 4000, true);
        if (this.app.isEmpty()) throw new IllegalArgumentException("اختر تطبيقًا.");
        switch (kind) {
            case OPEN_APP:
                if (!this.recipient.isEmpty() || !this.body.isEmpty()) throw new IllegalArgumentException("فتح التطبيق لا يتضمن إرسال نص.");
                break;
            case FACEBOOK_POST:
                if (!"facebook".equals(this.app) || !this.recipient.isEmpty() || this.body.isEmpty()) throw new IllegalArgumentException("بيانات منشور فيسبوك غير صالحة.");
                break;
            case WHATSAPP_MESSAGE:
                if (!"whatsapp".equals(this.app) || this.recipient.isEmpty() || this.body.isEmpty()) throw new IllegalArgumentException("تتطلب رسالة واتساب مستلمًا ونصًا.");
                break;
            default: throw new IllegalArgumentException("نوع المهمة غير مدعوم.");
        }
    }

    private static String value(String value, String label, int limit, boolean multiline) {
        if (value == null) throw new IllegalArgumentException(label + " غير صالح.");
        String clean = value.trim();
        if (clean.length() > limit) throw new IllegalArgumentException(label + " طويل جدًا.");
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (Character.isISOControl(c) && !(multiline && (c == '\n' || c == '\t'))) throw new IllegalArgumentException(label + " يحتوي محارف غير صالحة.");
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= clean.length() || !Character.isLowSurrogate(clean.charAt(++i))) throw new IllegalArgumentException(label + " غير صالح.");
            } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException(label + " غير صالح.");
        }
        return clean;
    }

    public static DevicePlan openApp(String app) { return new DevicePlan(Kind.OPEN_APP, app, "", ""); }
    public static DevicePlan facebookPost(String body) { return new DevicePlan(Kind.FACEBOOK_POST, "facebook", "", body); }
    public static DevicePlan whatsappMessage(String recipient, String body) { return new DevicePlan(Kind.WHATSAPP_MESSAGE, "whatsapp", recipient, body); }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try { json.put("kind", kind.name()); json.put("app", app); json.put("recipient", recipient); json.put("body", body); }
        catch (JSONException error) { throw new IllegalStateException(error); }
        return json;
    }

    public static DevicePlan fromJson(String json) throws JSONException {
        if (json == null || json.length() > 10000) throw new IllegalArgumentException("بيانات المهمة غير صالحة.");
        return fromJson(new JSONObject(json));
    }

    public static DevicePlan fromJson(JSONObject json) throws JSONException {
        if (json == null || json.length() != 4) throw new IllegalArgumentException("مخطط المهمة غير صالح.");
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!"kind".equals(key) && !"app".equals(key) && !"recipient".equals(key) && !"body".equals(key)) throw new IllegalArgumentException("حقل مهمة غير مدعوم.");
            if (!(json.get(key) instanceof String)) throw new IllegalArgumentException("حقول المهمة يجب أن تكون نصًا.");
        }
        Kind kind;
        try { kind = Kind.valueOf(json.getString("kind")); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("نوع المهمة غير مدعوم."); }
        return new DevicePlan(kind, json.getString("app"), json.getString("recipient"), json.getString("body"));
    }
}
