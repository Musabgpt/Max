package com.maxassistant.app.automation;

import org.junit.Test;
import static org.junit.Assert.*;

public final class DeviceTaskParserTest {
    @Test public void screenshotFacebookCommandNeedsNoModel() {
        DevicePlan plan = DeviceTaskParser.parse("افتح تطبيق فيسبوك وانشر مرحبا");
        assertNotNull(plan); assertEquals(DevicePlan.Kind.FACEBOOK_POST, plan.kind);
        assertEquals("facebook", plan.app); assertEquals("مرحبا", plan.body); assertEquals("", plan.recipient);
    }
    @Test public void recognizesCommonFacebookArabicAndEnglish() {
        assertEquals("مرحبا", DeviceTaskParser.parse("انشر على فيسبوك: «مرحبا»").body);
        assertEquals("مرحبا", DeviceTaskParser.parse("افتح فيسبوك وانشر:مرحبا").body);
        assertEquals("Hello!", DeviceTaskParser.parse("Please open Facebook and post \"Hello!\"").body);
        assertEquals("أهلًا\nبالجميع", DeviceTaskParser.parse("افتح فيس بوك ثم انشر أهلًا\nبالجميع").body);
    }
    @Test public void commonWhatsAppCommandSplitsRecipientAndBody() {
        DevicePlan plan = DeviceTaskParser.parse("افتح واتساب وارسل لمروة مرحبا");
        assertNotNull(plan); assertEquals(DevicePlan.Kind.WHATSAPP_MESSAGE, plan.kind);
        assertEquals("مروة", plan.recipient); assertEquals("مرحبا", plan.body);
    }
    @Test public void quotedAndColonMessagesAllowFullRecipientNames() {
        DevicePlan quoted = DeviceTaskParser.parse("افتح تطبيق واتساب وأرسل إلى مروة أحمد «مرحبًا: كيف حالك؟»");
        assertEquals("مروة أحمد", quoted.recipient); assertEquals("مرحبًا: كيف حالك؟", quoted.body);
        DevicePlan colon = DeviceTaskParser.parse("أرسل على واتساب إلى مروة أحمد: مرحبا بك");
        assertEquals("مروة أحمد", colon.recipient); assertEquals("مرحبا بك", colon.body);
        DevicePlan english = DeviceTaskParser.parse("open WhatsApp and send to Jane Doe: Hello Jane");
        assertEquals("Jane Doe", english.recipient); assertEquals("Hello Jane", english.body);
    }
    @Test public void urlInBodyIsPreserved() {
        DevicePlan plan = DeviceTaskParser.parse("افتح واتساب وارسل لمروة مرحبا https://example.com");
        assertEquals("مروة", plan.recipient); assertEquals("مرحبا https://example.com", plan.body);
    }
    @Test public void openGenericInstalledAppNameIsLocal() {
        assertEquals("الحاسبة", DeviceTaskParser.parse("افتح تطبيق الحاسبة").app);
        assertEquals(DevicePlan.Kind.OPEN_APP, DeviceTaskParser.parse("افتح فيسبوك").kind);
        assertEquals("com.example.app", DeviceTaskParser.parse("open app com.example.app").app);
    }
    @Test public void quotedInstructionsAndDraftRequestsRemainChat() {
        assertNull(DeviceTaskParser.parse("اكتب مسودة منشور فيسبوك مرحبا"));
        assertNull(DeviceTaskParser.parse("اشرح الأمر افتح تطبيق فيسبوك وانشر مرحبا"));
        assertNull(DeviceTaskParser.parse("«افتح تطبيق فيسبوك وانشر مرحبا»"));
        assertNull(DeviceTaskParser.parse("\"open Facebook and post hello\""));
        assertNull(DeviceTaskParser.parse("قل: افتح واتساب وارسل لمروة مرحبا"));
    }
    @Test public void leavesLegacyUrlBackFillAndClickCommandsAlone() {
        for (String input : new String[]{"افتح https://example.com", "افتح الرابط https://example.com", "افتح موقع example.com", "ارجع", "back", "املأ مرحبا", "fill hello", "اضغط نشر", "click Post", "افتح المتصفح"}) assertNull(input, DeviceTaskParser.parse(input));
    }
    @Test public void rejectsIncompleteOrUnsupportedMultiActionPlans() {
        assertNull(DeviceTaskParser.parse("افتح واتساب وارسل لمروة"));
        assertNull(DeviceTaskParser.parse("افتح فيسبوك وانشر"));
        assertNull(DeviceTaskParser.parse("افتح واتساب ثم احذف الرسائل"));
        assertNull(DeviceTaskParser.parse("open gmail and click send"));
        assertNull(DeviceTaskParser.parse(null));
    }
}
