package com.maxassistant.app.ai;

import java.util.Locale;

public final class TaskIntent {
    public enum Type {CHAT,CALCULATOR_HTML,GENERATE_HTML,RUN_LAST_HTML,DEVICE_REQUEST}
    private TaskIntent() { }
    public static Type classify(String text){
        String value=normalize(text);
        if(value.isEmpty()||value.length()>6000)return Type.CHAT;
        if(value.matches("^(كيف|هل|لماذا|اشرح|what|how|why)\\b.*"))return Type.CHAT;
        boolean html=value.contains("html")||value.contains("صفحه ويب")||value.contains("صفحة ويب");
        boolean creates=value.matches("^(اكتب|اعمل|اصنع|جهز|انشئ|ابني|build|create|write)\\b.*");
        if(html&&creates){
            if(value.contains("حاسب")||value.contains("calculator")||value.contains("اله حاس")||value.contains("اله حاسي"))return Type.CALCULATOR_HTML;
            if(wantsHtmlRun(value))return Type.GENERATE_HTML;
        }
        if(value.matches("^(شغل|شغله|شغلها|افتح المعاينه|اعرض المعاينه|run|preview)( الكود| اخر كود| المعاينه| الصفحه| html| it)?[.!؟?]*$"))return Type.RUN_LAST_HTML;
        String action=value.replaceFirst("^(من فضلك|لو سمحت|بالله|please)\\s+","");
        if(action.matches("^(افتح|قم بفتح|شغل تطبيق|ارسل|ابعث|انشر|open|send|post)\\b.*"))return Type.DEVICE_REQUEST;
        return Type.CHAT;
    }
    public static boolean wantsHtmlRun(String text){
        String value=normalize(text);
        return value.contains("شغل")||value.contains("افتحها")||value.contains("افتحه")||value.contains("المعاينه")||value.contains("preview")||value.matches(".*\\brun\\b.*");
    }
    public static String normalize(String text){
        return text==null?"":text.toLowerCase(Locale.ROOT).replaceAll("[\\u064b-\\u065f\\u0670\\u0640]","")
            .replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ة','ه').replace('ى','ي').replaceAll("\\s+"," ").trim();
    }
}
