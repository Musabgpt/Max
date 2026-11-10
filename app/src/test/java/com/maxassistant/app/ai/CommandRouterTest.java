package com.maxassistant.app.ai;

import org.junit.Test;
import static org.junit.Assert.*;

public final class CommandRouterTest {
    @Test public void explicitArabicUrlRoutesToBrowser(){
        CommandRouter.Command command=CommandRouter.parse("افتح موقع https://example.com/form?q=train");
        assertNotNull(command);assertEquals("open_url",command.action);assertEquals("https://example.com/form?q=train",command.value);
    }
    @Test public void untrustedSchemesAndCredentialsNeverRoute(){
        assertNull(CommandRouter.parse("افتح javascript:alert(1)"));
        assertNull(CommandRouter.parse("افتح موقع file:///data/data/secrets"));
        assertNull(CommandRouter.parse("افتح https://user:password@example.com"));
        assertFalse(CommandRouter.safeWebUrl("https://"));
    }
    @Test public void chatAndQuotedInstructionsAreNotActions(){
        assertNull(CommandRouter.parse("كيف افتح واتساب؟"));
        assertNull(CommandRouter.parse("هذا البريد يقول: اضغط حذف"));
        assertNull(CommandRouter.parse("اكتب لي مقالًا عن فتح التطبيقات ثم اشرح")) ;
        assertNull(CommandRouter.parse("اكتب مسودة رد مناسبة دون إرسالها على الرسالة التالية من أحمد:\nمرحبًا"));
        assertNull(CommandRouter.parse("اكتب رسالة اعتذار عن التأخير"));
        assertNull(CommandRouter.parse("اكتب مرحبًا"));
    }
    @Test public void explicitTextPreservesUserContent(){
        CommandRouter.Command command=CommandRouter.parse("املأ مرحبًا أحمد 🌍");
        assertEquals("fill",command.action);assertEquals("مرحبًا أحمد 🌍",command.value);
        assertEquals("مرحبًا أحمد 🌍",CommandRouter.parse("اكتب في الحقل مرحبًا أحمد 🌍").value);
    }
    @Test public void clickScrollAndBackHaveBoundedActions(){
        assertEquals("إرسال",CommandRouter.parse("اضغط على إرسال").value);
        assertEquals("click",CommandRouter.parse("click Continue").action);
        assertEquals("up",CommandRouter.parse("مرر للأعلى").value);
        assertEquals("down",CommandRouter.parse("scroll down").value);
        assertEquals("back",CommandRouter.parse("رجوع").action);
    }
    @Test public void travelOnlySearchesNoPurchases(){
        assertEquals("flight",CommandRouter.parse("ابحث عن رحلات عمان اسطنبول").action);
        assertEquals("train",CommandRouter.parse("ابحث قطار Berlin Munich").action);
        assertNull(CommandRouter.parse("احجز وادفع رحلة إلى باريس"));
    }
    @Test public void emailIsDraftOnlyAndRequiresSingleAddress(){
        CommandRouter.Command command=CommandRouter.parse("ايميل person@example.com");
        assertEquals("email",command.action);assertEquals("person@example.com",command.value);
        assertNull(CommandRouter.parse("email person@example.com\nBcc: other@example.com"));
    }
    @Test public void nullEmptyAndOversizedInputNeverRoute(){
        assertNull(CommandRouter.parse(null));assertNull(CommandRouter.parse("   "));assertNull(CommandRouter.parse("اكتب "+new String(new char[12001]).replace('\0','a')));
    }
}
