package qa.max11;

import android.Manifest;
import android.os.Bundle;
import android.widget.EditText;
import com.maxassistant.app.data.AppStore;

/** Fixture recognition error12 followed by actual recognition-Activity result handling. */
public final class Max11VoiceTest extends Max11UiBase {
    public void testUnsupportedArabicFallsBackAndRemainsUnsentDraft() throws Exception {
        String original=shell("settings get secure voice_recognition_service").trim();
        try {
            fixture("speech","reset",null);
            shell("settings put secure voice_recognition_service qa.speech/qa.speech.FixtureRecognitionService");
            shell("pm grant com.maxassistant.app android.permission.RECORD_AUDIO");
            int before;try(AppStore s=new AppStore(target)) {before=s.getMessages(500).size();}
            launchMain();
            int voice=target.getResources().getIdentifier("assistant_voice","id",target.getPackageName());
            assertTrue("Stable voice control ID exists",voice!=0);
            getInstrumentation().runOnMainSync(() -> {assertNotNull(main.findViewById(voice));assertTrue(main.findViewById(voice).performClick());});
            await("Unsupported-language provider invoked",()->state("speech").getInt("serviceCalls")==1,20000);
            await("Recognition fallback transcript placed in composer",()-> {
                int inputId=target.getResources().getIdentifier("assistant_input","id",target.getPackageName());
                final String[] text={""};final boolean[] visible={false};getInstrumentation().runOnMainSync(() -> {EditText e=main.findViewById(inputId);if(e!=null){text[0]=e.getText().toString();android.graphics.Rect bounds=new android.graphics.Rect();visible[0]=e.getGlobalVisibleRect(bounds)&&bounds.height()>=e.getHeight()/2;}});
                return "افتح واتساب".equals(text[0])&&visible[0];
            },30000);
            Bundle s=state("speech");assertEquals(1,s.getInt("serviceCalls"));assertEquals(1,s.getInt("activityCalls"));
            assertFalse("Arabic must not force offline recognition",s.getBoolean("preferOffline"));
            assertTrue("Proper Arabic language tag",s.getString("language").matches("ar-[A-Za-z]{2}"));
            assertEquals(s.getString("language"),s.getString("activityLanguage"));
            try(AppStore store=new AppStore(target)) {assertEquals("Voice transcript has not been sent or dispatched",before,store.getMessages(500).size());}
            screenshot("max11-voice-fallback-unsent-draft");
            System.out.println("VOICE_FIXTURE=error12 -> recognition Activity -> Arabic unsent draft; no microphone hardware claim");
        } finally {
            if(original.isEmpty()||"null".equals(original))shell("settings delete secure voice_recognition_service");
            else shell("settings put secure voice_recognition_service "+original);
        }
    }
}
