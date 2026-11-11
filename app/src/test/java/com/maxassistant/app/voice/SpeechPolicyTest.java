package com.maxassistant.app.voice;

import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.*;

public final class SpeechPolicyTest {
    @Test public void ArabicLocaleHasAConcreteBcp47Tag(){
        assertEquals("ar-JO",SpeechPolicy.language(new Locale("ar","JO")));
        assertEquals("ar-SA",SpeechPolicy.language(new Locale("ar")));
        assertEquals("ar-SA",SpeechPolicy.language(Locale.ENGLISH));
    }
    @Test public void UnsupportedLanguageUsesOneSystemFallback(){assertTrue(SpeechPolicy.shouldUseSystemFallback(12));assertTrue(SpeechPolicy.shouldUseSystemFallback(13));}
    @Test public void PermissionAndNoSpeechErrorsAreNotRetryLoops(){assertFalse(SpeechPolicy.shouldUseSystemFallback(9));assertFalse(SpeechPolicy.shouldUseSystemFallback(7));}
    @Test public void LanguageErrorExplainsActualRemedy(){assertTrue(SpeechPolicy.errorText(12).contains("العربية"));assertTrue(SpeechPolicy.errorText(13).contains("تنزيل"));}
}
