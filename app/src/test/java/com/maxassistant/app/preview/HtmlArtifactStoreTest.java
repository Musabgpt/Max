package com.maxassistant.app.preview;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public final class HtmlArtifactStoreTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private static final String HTML = "<html lang=\"ar\"><body>مرحبًا 🌍</body></html>";

    @Test public void roundTripsUnicodeUnderOpaquePrivateId() throws Exception {
        HtmlArtifactStore store = new HtmlArtifactStore(temporary.newFolder("artifacts"));
        String id = store.save(HTML);
        assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
        assertEquals(HTML, store.read(id));
    }

    @Test public void fencedHtmlFragmentsRemainRunnableAfterSaving() throws Exception {
        HtmlArtifactStore store = new HtmlArtifactStore(temporary.newFolder("artifacts"));
        String id = store.save("```html\n<button onclick=\"this.textContent='تم'\">اضغط</button>\n```");
        assertEquals("<button onclick=\"this.textContent='تم'\">اضغط</button>", store.read(id));
    }

    @Test public void totalByteCapacityIsEnforcedBeforeFileCountLimit() throws Exception {
        HtmlArtifactStore store = new HtmlArtifactStore(temporary.newFolder("artifacts"));
        String large = "<html>" + new String(new char[199987]).replace('\0', 'ع') + "</html>";
        String first = store.save(large);
        for (int i = 1; i < 20; i++) store.save(large);
        assertThrows(IOException.class, () -> store.save(large));
        assertEquals(large, store.read(first));
    }

    @Test public void refusesTraversalAbsolutePathsAndUnknownArtifacts() throws Exception {
        HtmlArtifactStore store = new HtmlArtifactStore(temporary.newFolder("artifacts"));
        for (String id : new String[]{null, "", "../max-local.db", "/data/user/0/keys", "550e8400-e29b-41d4-a716-446655440000/../keys", "550e8400-e29b-41d4-a716-446655440000.html"}) {
            assertThrows(IOException.class, () -> store.read(id));
        }
        assertThrows(IOException.class, () -> store.read("550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test public void capacityFailurePreservesAlreadySavedDocuments() throws Exception {
        File directory = temporary.newFolder("artifacts");
        HtmlArtifactStore store = new HtmlArtifactStore(directory);
        String first = store.save(HTML);
        for (int i = 1; i < 64; i++) store.save("<html><body>" + i + "</body></html>");
        assertThrows(IOException.class, () -> store.save(HTML));
        assertEquals(HTML, store.read(first));
        assertEquals(64, directory.listFiles().length);
    }

    @Test public void oversizedInputNeverCreatesAnArtifact() throws Exception {
        File directory = temporary.newFolder("artifacts");
        HtmlArtifactStore store = new HtmlArtifactStore(directory);
        String oversized = "<html>" + new String(new char[200001]).replace('\0', 'x') + "</html>";
        assertThrows(IllegalArgumentException.class, () -> store.save(oversized));
        assertEquals(0, directory.listFiles().length);
    }

    @Test public void oversizedOrCorruptOnDiskContentCannotBypassDocumentLimits() throws Exception {
        File directory = temporary.newFolder("artifacts");
        HtmlArtifactStore store = new HtmlArtifactStore(directory);
        String id = store.save(HTML);
        Files.write(new File(directory, id + ".html").toPath(), "null".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> store.read(id));
        byte[] oversized = new byte[800001];
        Files.write(new File(directory, id + ".html").toPath(), oversized);
        assertThrows(IOException.class, () -> store.read(id));
    }

    @Test public void refusesSymlinksOutsideThePrivateArtifactDirectory() throws Exception {
        File directory = temporary.newFolder("artifacts");
        HtmlArtifactStore store = new HtmlArtifactStore(directory);
        File outside = temporary.newFile("outside.html");
        Files.write(outside.toPath(), HTML.getBytes(StandardCharsets.UTF_8));
        String id = "550e8400-e29b-41d4-a716-446655440000";
        Files.createSymbolicLink(new File(directory, id + ".html").toPath(), outside.toPath());
        assertThrows(IOException.class, () -> store.read(id));
    }

    @Test public void explicitClearDeletesOnlyArtifactsAndRestoresCapacity() throws Exception {
        File directory = temporary.newFolder("artifacts");
        File memory = temporary.newFile("max-local.db");
        Files.write(memory.toPath(), "existing memory".getBytes(StandardCharsets.UTF_8));
        HtmlArtifactStore store = new HtmlArtifactStore(directory);
        String id = store.save(HTML);
        for (int i = 1; i < 64; i++) store.save(HTML);
        store.clear();
        assertThrows(IOException.class, () -> store.read(id));
        assertEquals("existing memory", new String(Files.readAllBytes(memory.toPath()), StandardCharsets.UTF_8));
        assertEquals(HTML, store.read(store.save(HTML)));
    }
}
