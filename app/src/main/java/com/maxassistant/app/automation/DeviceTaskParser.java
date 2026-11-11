package com.maxassistant.app.automation;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative local recognition of explicit app tasks. Quoted instructions remain ordinary chat. */
public final class DeviceTaskParser {
    private DeviceTaskParser() { }
    private static final String FB = "(?:فيس\\s*بوك|الفيس\\s*بوك|فيسبوك\\s*لايت|facebook(?:\\s+lite)?)";
    private static final String WA = "(?:واتس\\s*[اآ]ب|الواتس\\s*[اآ]ب|واتساب|الواتساب|whatsapp(?:\\s+business)?)";
    private static final String OPEN = "(?:[اأإ]فتح|شغل|شغّل|open|launch)(?:\\s+(?:لي|ل[ىي]))?\\s+(?:(?:تطبيق|app|the\\s+app)\\s+)?";
    private static final String JOIN = "\\s*(?:و\\s*|ثم\\s+|and\\s+)";
    private static final String POST = "(?:[اأ]نشر|انشر|post)(?:\\s+(?:منشور[ًاا]?|a\\s+post))?";
    private static final String SEND = "(?:[اأ]رسل|ارسل|[اأ]بعث|send)(?:\\s+(?:رسالة|a\\s+message|message))?";
    private static final String BODY_SEPARATOR = "(?:\\s*[:：]\\s*|\\s+)";
    private static final Pattern FB_OPEN = pattern("^" + OPEN + FB + JOIN + POST + BODY_SEPARATOR + "(.+)$");
    private static final Pattern FB_DIRECT = pattern("^" + POST + "\\s+(?:على|في|on|to)\\s+" + FB + BODY_SEPARATOR + "(.+)$");
    private static final Pattern WA_OPEN = pattern("^" + OPEN + WA + JOIN + SEND + "\\s+(.+)$");
    private static final Pattern WA_DIRECT = pattern("^" + SEND + "\\s+(?:عبر|على|في|on|via)\\s+" + WA + "\\s+(.+)$");
    private static final Pattern WA_NAMED = pattern("^" + SEND + "\\s+(?:(?:إلى|الى|ل|to)\\s*)?(.+?)\\s+(?:عبر|على|في|on|via)\\s+" + WA + BODY_SEPARATOR + "(.+)$");
    private static final Pattern OPEN_APP = pattern("^" + OPEN + "(.+)$");
    private static Pattern pattern(String regex) { return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL); }

    public static DevicePlan parse(String input) {
        if (input == null || input.length() > 6000) return null;
        String text = input.trim().replaceFirst("(?iu)^(?:من فضلك|لو سمحت|please)[،,]?\\s+", "");
        if (text.isEmpty() || text.startsWith("\"") || text.startsWith("«") || text.startsWith("“") || text.startsWith("'") || text.startsWith("`")) return null;
        try {
            Matcher m = FB_OPEN.matcher(text);
            if (m.matches()) return DevicePlan.facebookPost(body(m.group(1)));
            m = FB_DIRECT.matcher(text);
            if (m.matches()) return DevicePlan.facebookPost(body(m.group(1)));
            m = WA_OPEN.matcher(text);
            if (m.matches()) return whatsappTail(m.group(1));
            m = WA_DIRECT.matcher(text);
            if (m.matches()) return whatsappTail(m.group(1));
            m = WA_NAMED.matcher(text);
            if (m.matches()) return DevicePlan.whatsappMessage(m.group(1).trim(), body(m.group(2)));
            m = OPEN_APP.matcher(text);
            if (m.matches()) {
                String app = m.group(1).trim();
                String lower = app.toLowerCase(Locale.ROOT);
                if (app.length() > 120 || app.contains("\n") || app.contains(":") || app.contains("/") || app.contains("\\") || app.contains("\"") || app.contains("«") || app.contains("`")) return null;
                if (lower.matches(".*(?:\\s+(?:and|then|ثم)\\s+|\\s+و(?=افتح|أرسل|ارسل|انشر|اضغط|املأ)).*") || lower.matches("^(?:الرابط|رابط|موقع|url|website|browser|المتصفح|ارجع|رجوع|back|fill|امل[اأ]|اضغط|click)(?:\\s.*)?$")) return null;
                return DevicePlan.openApp(app);
            }
        } catch (IllegalArgumentException error) { return null; }
        return null;
    }

    private static DevicePlan whatsappTail(String text) {
        String tail = text.trim();
        Matcher lead = pattern("^(?:إلى|الى|لـ?|to)\\s*(.+)$").matcher(tail);
        if (!lead.matches()) return null;
        tail = lead.group(1).trim();
        Matcher quoted = pattern("^(.{1,120}?)\\s+([\"'«“])(.+)([\"'»”])$").matcher(tail);
        if (quoted.matches() && quotesMatch(quoted.group(2), quoted.group(4))) return DevicePlan.whatsappMessage(quoted.group(1).trim(), quoted.group(3));
        Matcher colon = pattern("^([^\"'«“:：]{1,120}?)\\s*[:：]\\s*(?!//)(.+)$").matcher(tail);
        if (colon.matches()) return DevicePlan.whatsappMessage(colon.group(1).trim(), body(colon.group(2)));
        int split = tail.indexOf(' ');
        if (split < 1) return null;
        return DevicePlan.whatsappMessage(tail.substring(0, split), body(tail.substring(split + 1)));
    }

    private static boolean quotesMatch(String first, String last) { return first.equals(last) || ("«".equals(first) && "»".equals(last)) || ("“".equals(first) && "”".equals(last)); }
    private static String body(String text) {
        String value = text.trim();
        if (value.startsWith(":")) value = value.substring(1).trim();
        if (value.length() >= 2 && quotesMatch(value.substring(0, 1), value.substring(value.length() - 1)) && "\"'«“".contains(value.substring(0, 1))) return value.substring(1, value.length() - 1).trim();
        return value;
    }
}
