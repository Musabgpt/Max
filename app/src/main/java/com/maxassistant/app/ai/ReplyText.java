package com.maxassistant.app.ai;

import java.io.IOException;
import java.util.Locale;

public final class ReplyText {
    private ReplyText() { }
    static String clean(String value){return value==null?"":value.replace("\u200b","").replace("\ufeff","").trim();}
    public static boolean isUsable(String value){
        String text=clean(value).toLowerCase(Locale.ROOT);
        return !text.isEmpty()&&!text.equals("null")&&!text.equals("undefined")&&!text.equals("none")&&!text.equals("[object object]");
    }
    public static String require(String value) throws IOException {
        if(!isUsable(value))throw new IOException("لم يرجع النموذج جوابًا مكتملًا. أعد الطلب أو اختر نموذجًا آخر.");
        return clean(value);
    }
    public static String forDisplay(String value){return isUsable(value)?clean(value):"رد سابق غير مكتمل. أعد الطلب بالتحديث الجديد.";}
}
