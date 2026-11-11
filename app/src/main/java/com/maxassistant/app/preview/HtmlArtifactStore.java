package com.maxassistant.app.preview;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

/** HTML files kept under a private app directory and addressed by opaque identifiers. */
public final class HtmlArtifactStore {
    private static final int MAX_FILES = 64;
    private static final long MAX_TOTAL_BYTES = 8L * 1024 * 1024;
    private static final int MAX_DOCUMENT_BYTES = HtmlDocuments.MAX_HTML_CHARS * 4;
    private static final Pattern ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final Object LOCK = new Object();
    private final File directory;
    public HtmlArtifactStore(Context context) { this(new File(context.getFilesDir(), "html-artifacts")); }
    HtmlArtifactStore(File directory) { this.directory = directory; }

    /** Capacity is explicit: existing referenced previews are never evicted silently. */
    public String save(String html) throws IOException {
        String document = HtmlDocuments.extract(html);
        byte[] bytes = document.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_DOCUMENT_BYTES) throw new IOException("كود المعاينة أكبر من الحد المسموح.");
        synchronized (LOCK) {
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("تعذّر إنشاء مجلد المعاينات المحلي.");
            File[] files = directory.listFiles();
            if (files == null) throw new IOException("تعذّر قراءة مجلد المعاينات المحلي.");
            long total = 0;
            for (File file : files) total += file.length();
            if (files.length >= MAX_FILES || total + bytes.length > MAX_TOTAL_BYTES)
                throw new IOException("بلغ تخزين المعاينات المحلي حدّه. الكود محفوظ في المحادثة ويمكنك نسخه.");
            String id = UUID.randomUUID().toString();
            File target = artifact(id);
            File draft = File.createTempFile("draft-", ".tmp", directory);
            try {
                try (FileOutputStream output = new FileOutputStream(draft)) {
                    output.write(bytes);
                    output.getFD().sync();
                }
                if (!draft.renameTo(target)) throw new IOException("تعذّر حفظ المعاينة محليًا.");
            } finally {
                if (draft.exists() && !draft.delete()) draft.deleteOnExit();
            }
            return id;
        }
    }

    public String read(String id) throws IOException {
        synchronized (LOCK) {
            File file = artifact(id);
            if (!file.isFile() || file.length() > MAX_DOCUMENT_BYTES)
                throw new IOException("ملف المعاينة غير موجود أو يتجاوز الحد المسموح. يمكنك إنشاء المعاينة من الكود مجددًا.");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) Math.min(file.length(), 8192));
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > MAX_DOCUMENT_BYTES) throw new IOException("ملف المعاينة أكبر من الحد المسموح.");
                    bytes.write(buffer, 0, count);
                }
            }
            try {
                String document = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
                return HtmlDocuments.extract(document);
            } catch (IllegalArgumentException | CharacterCodingException invalid) {
                throw new IOException("ملف المعاينة غير صالح. يمكنك إنشاء المعاينة من الكود مجددًا.", invalid);
            }
        }
    }

    /** Used by the explicit clear-conversation action; never deletes database/preferences. */
    public void clear() throws IOException {
        synchronized (LOCK) {
            if (!directory.exists()) return;
            File[] files = directory.listFiles();
            if (files == null) throw new IOException("تعذّر قراءة مجلد المعاينات لمسحه.");
            boolean failed = false;
            for (File file : files) if (!file.delete() && file.exists()) failed = true;
            if (failed) throw new IOException("تعذّر مسح بعض ملفات المعاينة المحلية. جرّب مسح المحادثة مجددًا.");
        }
    }

    private File artifact(String id) throws IOException {
        if (id == null || !ID.matcher(id).matches()) throw new IOException("معرّف المعاينة غير صالح.");
        File file = new File(directory, id + ".html");
        if (!directory.getCanonicalFile().equals(file.getCanonicalFile().getParentFile()))
            throw new IOException("مسار المعاينة غير صالح.");
        return file;
    }
}
