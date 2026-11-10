package com.maxassistant.app.ai;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicit, deterministic user commands. No generated text is passed to this parser. */
public final class CommandRouter {
    private CommandRouter(){}
    public static final class Command {
        public final String action,value,summary;
        public Command(String action,String value,String summary){this.action=action;this.value=value;this.summary=summary;}
    }
    private static final Pattern URL_COMMAND=Pattern.compile("^(?:افتح(?: الموقع| موقع)?|فتح موقع|open(?: website| url)?)\\s+(https?://\\S+)$",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern TEXT_COMMAND=Pattern.compile("^(اكتب في الحقل|املأ|املا|fill|type)\\s+(.+)$",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE|Pattern.DOTALL);
    private static final Pattern CLICK_COMMAND=Pattern.compile("^(?:اضغط(?: على)?|انقر(?: على)?|click)\\s+(.+)$",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern FLIGHT=Pattern.compile("^(?:ابحث(?: عن)? (?:رحلات|طيران|رحلة)|ابحث رحلات|search flights?|find flights?)\\s+(.+)$",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern TRAIN=Pattern.compile("^(?:ابحث(?: عن)? (?:قطار|قطارات)|search trains?|find trains?)\\s+(.+)$",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern EMAIL=Pattern.compile("^(?:ايميل|إيميل|بريد إلى|بريد الى|email)\\s+([A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9.-]*[A-Z0-9])?\\.[A-Z]{2,})$",Pattern.CASE_INSENSITIVE);
    public static Command parse(String input){
        if(input==null)return null;
        String text=input.trim();if(text.isEmpty()||text.length()>12000)return null;
        Matcher match=URL_COMMAND.matcher(text);
        if(match.matches()){
            String url=match.group(1);if(!safeWebUrl(url))return null;return new Command("open_url",url,"فتح الموقع: "+url);
        }
        String lower=text.toLowerCase(Locale.ROOT);
        if(lower.equals("افتح واتساب")||lower.equals("افتح الواتساب")||lower.equals("open whatsapp"))return new Command("open_app","com.whatsapp","فتح واتساب");
        if(lower.equals("افتح gmail")||lower.equals("افتح جيميل")||lower.equals("open gmail"))return new Command("open_app","com.google.android.gm","فتح Gmail");
        if(lower.equals("افتح متصفح")||lower.equals("افتح المتصفح")||lower.equals("open browser"))return new Command("open_url","https://www.google.com/","فتح المتصفح");
        if(lower.equals("رجوع")||lower.equals("ارجع")||lower.equals("back"))return new Command("back","","الرجوع في التطبيق الحالي");
        if(lower.equals("اسحب")||lower.equals("مرر")||lower.equals("مرّر")||lower.equals("مرر للأسفل")||lower.equals("مرر للاسفل")||lower.equals("اسحب للأسفل")||lower.equals("scroll down"))return new Command("scroll","down","تمرير الشاشة إلى الأسفل");
        if(lower.equals("مرر للأعلى")||lower.equals("مرر للاعلى")||lower.equals("اسحب للأعلى")||lower.equals("scroll up"))return new Command("scroll","up","تمرير الشاشة إلى الأعلى");
        // Writing/composition belongs to chat; screen input requires an explicit field command.
        match=TEXT_COMMAND.matcher(text);if(match.matches())return new Command("fill",match.group(2).trim(),"كتابة النص في الحقل المحدد بعد المراجعة");
        match=CLICK_COMMAND.matcher(text);if(match.matches())return new Command("click",match.group(1).trim(),"الضغط على: "+match.group(1).trim());
        match=FLIGHT.matcher(text);if(match.matches())return new Command("flight",match.group(1).trim(),"البحث عن رحلات: "+match.group(1).trim());
        match=TRAIN.matcher(text);if(match.matches())return new Command("train",match.group(1).trim(),"البحث عن قطار: "+match.group(1).trim());
        match=EMAIL.matcher(text);if(match.matches())return new Command("email",match.group(1),"فتح مسودة بريد إلى: "+match.group(1));
        return null;
    }
    public static boolean safeWebUrl(String value){
        if(value==null||value.length()>4096)return false;
        try{URI uri=new URI(value);return ("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))&&uri.getHost()!=null&&!uri.getHost().isEmpty()&&uri.getRawUserInfo()==null;}catch(URISyntaxException e){return false;}
    }
}
