package com.maxassistant.app.preview;

import org.junit.Test;
import static org.junit.Assert.*;

public final class HtmlDocumentsTest {
    private static final String DOCUMENT = "<!DOCTYPE html><html lang=\"ar\"><head><meta charset=\"utf-8\"></head><body><button>احسب</button></body></html>";

    @Test public void extractsCompleteDocumentWithoutSurroundingExplanation() {
        assertEquals(DOCUMENT, HtmlDocuments.extract("هذا هو الكود:\n" + DOCUMENT + "\nافتح المعاينة."));
        assertTrue(HtmlDocuments.looksLikeHtml(DOCUMENT));
    }

    @Test public void extractsFencedHtmlIncludingUsefulFragments() {
        assertEquals(DOCUMENT, HtmlDocuments.extract("جاهز\n```html\n" + DOCUMENT + "\n```\nشغّله"));
        assertEquals("<button onclick=\"this.textContent='تم'\">اضغط</button>",
            HtmlDocuments.extract("```HTML\n<button onclick=\"this.textContent='تم'\">اضغط</button>\n```"));
    }

    @Test public void rejectsSentinelsPlainTextAndIncompleteMarkup() {
        for (String invalid : new String[]{null, "", "null", "undefined", "None", "شرح عادي", "<html><body>مبتور", "```html\nnull\n```", "```html\n<button>مبتور", "```text\nنص فقط\n```"}) {
            assertFalse("Unexpected HTML: " + invalid, HtmlDocuments.looksLikeHtml(invalid));
            assertThrows(IllegalArgumentException.class, () -> HtmlDocuments.extract(invalid));
        }
    }

    @Test public void enforcesBoundWithoutReturningATruncatedDocument() {
        String large = "<html><body>" + repeat('x', 200001) + "</body></html>";
        assertFalse(HtmlDocuments.looksLikeHtml(large));
        assertThrows(IllegalArgumentException.class, () -> HtmlDocuments.extract(large));
        String atLimit = "<html>" + repeat('x', 200000 - "<html></html>".length()) + "</html>";
        assertEquals(atLimit, HtmlDocuments.extract(atLimit));
    }

    @Test public void rejectsHugeNonHtmlBeforeSearchingIt() {
        assertThrows(IllegalArgumentException.class, () -> HtmlDocuments.extract(repeat('x', 400000)));
    }

    @Test public void fencesDoNotTurnRandomWordsOrTagShapedValuesIntoHtml() {
        assertThrows(IllegalArgumentException.class, () -> HtmlDocuments.extract("```html\nhello <name>world</name>\n```"));
        assertThrows(IllegalArgumentException.class, () -> HtmlDocuments.extract("html is a language, <button> is a tag"));
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
    }
}
