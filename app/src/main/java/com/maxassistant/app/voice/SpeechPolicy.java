package com.maxassistant.app.voice;

import java.util.Locale;

public final class SpeechPolicy {
    private SpeechPolicy() { }
    public static String language(Locale locale){
        if(locale!=null&&"ar".equals(locale.getLanguage())&&!locale.getCountry().isEmpty())return locale.toLanguageTag();
        return "ar-SA";
    }
    public static boolean shouldUseSystemFallback(int error){return error==12||error==13||error==11||error==4||error==5;}
    public static String errorText(int error){
        switch(error){
            case 12:return "خدمة الصوت الحالية لا تدعم العربية المطلوبة. اختر خدمة تدعم العربية من إعدادات الصوت.";
            case 13:return "العربية مدعومة لكن حزمة اللغة غير جاهزة. أكمل تنزيل العربية في خدمة الصوت أو استخدم التعرف عبر الإنترنت.";
            case 1:case 2:return "تعذّر اتصال خدمة الصوت. تحقق من الإنترنت أو حزمة اللغة المحلية ثم حاول مجددًا.";
            case 3:return "الميكروفون غير متاح الآن. أغلق التسجيل في التطبيقات الأخرى ثم أعد المحاولة.";
            case 6:case 7:return "لم ألتقط كلامًا واضحًا. اضغط زر الصوت وحاول مجددًا.";
            case 8:return "خدمة الصوت مشغولة. أوقف التسجيل الآخر ثم حاول مجددًا.";
            case 9:return "يلزم السماح بالميكروفون من صلاحيات التطبيق لاستخدام الصوت.";
            default:return "خدمة التعرف على الصوت غير جاهزة. اختر خدمة صوت من إعدادات الهاتف ثم أعد المحاولة.";
        }
    }
}
