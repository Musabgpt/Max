package qa.max11;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import com.maxassistant.app.automation.AutomationService;
import com.maxassistant.app.automation.DevicePlan;

/** Real Android accessibility against native isolated fixture apps, never real accounts. */
public final class Max11DeviceTest extends Max11UiBase {
    private static final String BODY="اختبار MAX محلي فقط 2 + 3";
    void enableMax() throws Exception {
        shell("settings put secure enabled_accessibility_services com.maxassistant.app/com.maxassistant.app.automation.MaxAccessibilityService");
        shell("settings put secure accessibility_enabled 1");
        await("MAX accessibility service connected",()->com.maxassistant.app.automation.MaxAccessibilityService.isConnected(),25000);
    }
    void review(DevicePlan plan) throws Exception {
        enableMax();Activity activity=launchMain();
        getInstrumentation().runOnMainSync(() -> AutomationService.reviewAndStart(activity,plan));
        await("Concrete plan review is visible",()->find("مراجعة مهمة الهاتف",false)!=null,15000);
    }
    void facebookDraft() throws Exception {
        fixture("facebook","reset",null);review(DevicePlan.facebookPost(BODY));
        assertEquals("No commit before initial review",0,state("facebook").getInt("commitCount"));
        click("ابدأ تجهيز المسودة");
        await("Facebook final confirmation overlay",()->find("max_task_confirm",true)!=null,30000);
        Bundle s=state("facebook");assertEquals(BODY,s.getString("draft"));assertEquals(0,s.getInt("commitCount"));assertEquals(0,s.getInt("shareCount"));
        screenshot("max11-facebook-draft-awaiting-confirmation");
    }
    public void testInitialReviewCancellationDoesNotLaunchOrCommit() throws Exception {
        fixture("facebook","reset",null);review(DevicePlan.facebookPost(BODY));click("إلغاء");
        assertFalse(AutomationService.isRunning());assertEquals(0,state("facebook").getInt("commitCount"));
        assertEquals("",state("facebook").getString("stage"));
    }
    public void testScreenshotCommandRoutesFromChatWithoutModel() throws Exception {
        fixture("facebook","reset",null);enableMax();launchMain();
        int inputId=target.getResources().getIdentifier("assistant_input","id",target.getPackageName());
        int sendId=target.getResources().getIdentifier("assistant_send","id",target.getPackageName());
        getInstrumentation().runOnMainSync(() -> {
            android.widget.EditText input=main.findViewById(inputId);assertNotNull(input);
            input.setText("افتح تطبيق فيسبوك وانشر مرحبا");assertTrue(main.findViewById(sendId).performClick());
        });
        await("Screenshot command opens concrete review without a downloaded model",()->find("مراجعة مهمة الهاتف",false)!=null,15000);
        assertEquals(0,state("facebook").getInt("commitCount"));
        screenshot("max11-facebook-chat-command-review");click("إلغاء");
        assertFalse(AutomationService.isRunning());assertEquals(0,state("facebook").getInt("commitCount"));
    }
    public void testFinalConfirmationCommitsExactlyOnce() throws Exception {
        facebookDraft();AccessibilityNodeInfo confirm=find("max_task_confirm",true);
        assertNotNull(confirm);assertTrue(confirm.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        // Replay the stale accessible node immediately; a second commit must remain impossible.
        confirm.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        await("Exactly one fixture commit",()->state("facebook").getInt("commitCount")==1,15000);
        await("Session is finished",()->!AutomationService.isRunning(),15000);
        assertEquals(BODY,state("facebook").getString("committedDraft"));
        SystemClock.sleep(300);assertEquals(1,state("facebook").getInt("commitCount"));
    }
    public void testCancelPreparedDraftNeverCommits() throws Exception {
        facebookDraft();clickDescription("max_task_cancel");
        await("Cancelled session stops",()->!AutomationService.isRunning(),15000);
        assertEquals(0,state("facebook").getInt("commitCount"));assertEquals(BODY,state("facebook").getString("draft"));
    }
    public void testEditedDraftIsRejectedAtFinalConfirmation() throws Exception {
        facebookDraft();fixture("facebook","draft","Changed local fixture draft");
        await("User edit reaches real editor",()->"Changed local fixture draft".equals(state("facebook").getString("draft")),5000);
        AccessibilityNodeInfo confirm=find("max_task_confirm",true);
        if(confirm!=null)confirm.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        await("Edited draft cancels session",()->!AutomationService.isRunning(),15000);
        assertEquals(0,state("facebook").getInt("commitCount"));
    }
    public void testWrongForegroundAppCancelsPreparedCommit() throws Exception {
        facebookDraft();AccessibilityNodeInfo stale=find("max_task_confirm",true);
        shell("am start -W -n com.whatsapp/qa.fixture.FixtureActivity");
        if(stale!=null)stale.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        await("Wrong app ends session",()->!AutomationService.isRunning(),15000);
        assertEquals(0,state("facebook").getInt("commitCount"));
    }
    public void testLockedDeviceCancelsPreparedCommit() throws Exception {
        facebookDraft();AccessibilityNodeInfo stale=find("max_task_confirm",true);
        assertFalse("Disposable test emulator must start without a user credential",target.getSystemService(KeyguardManager.class).isDeviceSecure());
        String configured=shell("locksettings set-pin 2468");
        try {
            assertTrue(configured,configured.toLowerCase(java.util.Locale.ROOT).contains("pin set"));
            shell("input keyevent KEYCODE_SLEEP");
            await("Android reports locked",()->target.getSystemService(KeyguardManager.class).isKeyguardLocked(),10000);
            if(stale!=null)stale.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            await("Lock ends session",()->!AutomationService.isRunning(),15000);
            assertEquals(0,state("facebook").getInt("commitCount"));
        } finally {
            String cleared=shell("locksettings clear --old 2468");assertTrue(cleared,cleared.toLowerCase(java.util.Locale.ROOT).contains("cleared"));
            // API26 can keep the old PIN bouncer after credential removal. Refresh
            // the nonsecure keyguard before later cases; never enter a real user PIN.
            shell("input keyevent KEYCODE_BACK");shell("input keyevent KEYCODE_SLEEP");
            shell("input keyevent KEYCODE_WAKEUP");shell("input keyevent KEYCODE_MENU");
            await("Fixture credential cleared and device unlocked",()->!target.getSystemService(KeyguardManager.class).isDeviceSecure()&&!target.getSystemService(KeyguardManager.class).isKeyguardLocked(),15000);
        }
    }
    public void testHomeCancelsPreparedCommit() throws Exception {
        facebookDraft();AccessibilityNodeInfo stale=find("max_task_confirm",true);shell("input keyevent KEYCODE_HOME");
        if(stale!=null)stale.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        await("Leaving target for Home ends session",()->!AutomationService.isRunning(),15000);
        assertEquals(0,state("facebook").getInt("commitCount"));
    }
    public void testWhatsAppRecipientAndExplicitSingleCommit() throws Exception {
        fixture("whatsapp","reset",null);review(DevicePlan.whatsappMessage("مروة",BODY));
        assertEquals(0,state("whatsapp").getInt("commitCount"));click("ابدأ تجهيز المسودة");
        await("WhatsApp final confirmation",()->find("max_task_confirm",true)!=null,30000);
        Bundle s=state("whatsapp");assertEquals("مروة",s.getString("recipient"));assertEquals(BODY,s.getString("draft"));assertEquals(0,s.getInt("commitCount"));
        clickDescription("max_task_confirm");await("One local WhatsApp commit",()->state("whatsapp").getInt("commitCount")==1,15000);
        assertEquals("مروة",state("whatsapp").getString("committedRecipient"));assertEquals(BODY,state("whatsapp").getString("committedDraft"));
    }
    public void testChangedWhatsAppRecipientIsRejected() throws Exception {
        fixture("whatsapp","reset",null);review(DevicePlan.whatsappMessage("مروة",BODY));click("ابدأ تجهيز المسودة");
        await("WhatsApp final confirmation",()->find("max_task_confirm",true)!=null,30000);
        fixture("whatsapp","recipient","عمر");AccessibilityNodeInfo stale=find("max_task_confirm",true);
        if(stale!=null)stale.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        await("Recipient change ends session",()->!AutomationService.isRunning(),15000);
        assertEquals(0,state("whatsapp").getInt("commitCount"));
    }
}
