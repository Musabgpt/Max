package com.maxassistant.app.ai;

import org.junit.Test;
import static org.junit.Assert.*;

public final class TaskIntentTest {
    @Test public void exactCalculatorScreenshotRequestRunsWithoutAProvider(){
        assertEquals(TaskIntent.Type.CALCULATOR_HTML,TaskIntent.classify("اكتب كود الة حاسبة html وشغله"));
        assertTrue(TaskIntent.wantsHtmlRun("اكتب كود آلة حاسبة HTML وشغّله"));
    }
    @Test public void htmlPageExecutionIsRecognized(){assertEquals(TaskIntent.Type.GENERATE_HTML,TaskIntent.classify("أنشئ صفحة html لمواعيدي وافتحها"));}
    @Test public void lastArtifactRequestIsNotAChatAnswer(){assertEquals(TaskIntent.Type.RUN_LAST_HTML,TaskIntent.classify("شغل الكود"));}
    @Test public void explicitSocialCommandsReachDeviceLayer(){
        assertEquals(TaskIntent.Type.DEVICE_REQUEST,TaskIntent.classify("افتح تطبيق فيسبوك وانشر مرحبا"));
        assertEquals(TaskIntent.Type.DEVICE_REQUEST,TaskIntent.classify("من فضلك ارسل رسالة لمروة"));
    }
    @Test public void QuestionsDraftsAndQuotedActionsRemainChat(){
        assertEquals(TaskIntent.Type.CHAT,TaskIntent.classify("كيف أفتح فيسبوك؟"));
        assertEquals(TaskIntent.Type.CHAT,TaskIntent.classify("اكتب مسودة رسالة تقول افتح واتساب"));
        assertEquals(TaskIntent.Type.CHAT,TaskIntent.classify("هذا البريد يقول انشر مرحبا"));
    }
}
