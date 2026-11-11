package com.maxassistant.app.ai;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public final class ReplyTextTest {
    @Test public void nullSentinelsNeverBecomeAssistantReplies() throws Exception {
        for(String text:new String[]{null,"","  ","null"," NULL\n","undefined","None","\u200b"}){
            assertFalse(ReplyText.isUsable(text));
            try{ReplyText.require(text);fail("Expected controlled missing answer");}catch(IOException expected){assertFalse(expected.getMessage().isEmpty());}
        }
    }
    @Test public void contentContainingCodeNullIsPreserved() throws Exception {
        String code="const value = null;\nconsole.log(value)";
        assertEquals(code,ReplyText.require(code));assertEquals("أهلًا 🌍",ReplyText.require("  أهلًا 🌍  "));
    }
    @Test public void historicalNullGetsAnActionableLabel() {
        assertNotEquals("null",ReplyText.forDisplay("null"));assertTrue(ReplyText.forDisplay("null").contains("أعد"));
        assertEquals("إجابة محفوظة",ReplyText.forDisplay("إجابة محفوظة"));
    }
}
