package com.maxassistant.app.automation;

import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/** Allowlisted local UI stages only. All selectors and all observed screen text stay on this device. */
final class DeviceController {
    private enum Stage { FIND_COMPOSER, WAIT_FACEBOOK_COMPOSER, FIND_RECIPIENT, WAIT_SEARCH, WAIT_WHATSAPP_COMPOSER, VERIFY_BODY, WAIT_CONFIRM, COMMITTING }
    private final MaxAccessibilityService service;
    private final AutomationService.Session task;
    private final AutomationService owner;
    private final CommitGuard guard;
    private final TaskOverlay overlay;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Stage stage;
    private boolean finished, seenTarget, scheduled, searched, openedSearch, clickedNext;
    private long stageStarted, notBefore;
    private long reviewRevision;
    private int navigationActions;
    private final Runnable tick = () -> { scheduled = false; inspect(); schedule(400); };

    DeviceController(MaxAccessibilityService service, AutomationService.Session task, AutomationService owner) {
        this.service = service; this.task = task; this.owner = owner;
        guard = new CommitGuard(task.id, task.packageName, task.plan.recipient, task.plan.body, task.startedAt);
        overlay = new TaskOverlay(service, () -> finish("أُلغيت مهمة الهاتف. راجع المسودة في التطبيق؛ لم أضغط إرسالًا أو نشرًا نهائيًا."));
        stage = task.plan.kind == DevicePlan.Kind.FACEBOOK_POST ? Stage.FIND_COMPOSER : Stage.FIND_RECIPIENT;
        stageStarted = now();
    }
    String id() { return task.id; }
    void start() { overlay.show("أنتظر واجهة التطبيق المحدد…"); schedule(250); }
    void onEvent() { schedule(180); }
    private static long now() { return SystemClock.elapsedRealtime(); }
    private void schedule(long delay) { if (!finished && !scheduled) { scheduled = true; handler.postDelayed(tick, delay); } }
    void stopSilently() { finished = true; guard.cancel(); handler.removeCallbacksAndMessages(null); overlay.close(); }
    void disconnected() { finish("توقفت خدمة إمكانية الوصول. لن أتابع أي إرسال أو نشر؛ راجع المسودة في التطبيق."); }
    private void finish(String message) {
        if (finished) return;
        stopSilently(); owner.complete(task.id, message);
    }
    private void transition(Stage next, String message) {
        stage = next; stageStarted = now(); notBefore = now() + 500;
        overlay.show(message);
    }

    private void inspect() {
        if (finished) return;
        long clock = now();
        if (guard.expired(clock)) { finish("انتهت مهلة مهمة الهاتف. راجع المسودة؛ لم أتابع الإرسال أو النشر."); return; }
        if (!MaxAccessibilityService.deviceUnlocked(service)) { finish("توقفت المهمة لأن الهاتف مقفل أو الشاشة مطفأة. افتحه وأعد المراجعة؛ لن أستأنف الإرسال تلقائيًا."); return; }
        if (stage == Stage.COMMITTING) return;
        try (MaxAccessibilityService.AppWindow window = service.appWindow(task.packageName)) {
            if (window == null) {
                if (seenTarget) finish("تغيّر التطبيق أو النافذة النشطة؛ أُلغيت المهمة. راجع المسودة ثم أعد الطلب من MAX.");
                else if (clock - task.startedAt > 8000) finish("لم تظهر نافذة التطبيق المحدد. افتحه وسجّل الدخول يدويًا ثم أعد الطلب.");
                return;
            }
            seenTarget = true;
            List<AccessibilityNodeInfo> nodes = visibleNodes(window.root);
            try {
                if (containsPassword(nodes)) { finish("تتطلب الشاشة تسجيل دخول أو حقلًا سريًا. أكمل ذلك بنفسك ثم أعد الطلب."); return; }
                if (stage == Stage.COMMITTING || clock < notBefore) return;
                if (stage == Stage.WAIT_CONFIRM) { validateReview(window, nodes); return; }
                boolean facebook = task.plan.kind == DevicePlan.Kind.FACEBOOK_POST;
                Composer composer = composer(window, nodes, facebook);
                try { if (composer != null) {
                    if (!facebook && !composer.recipientMatches) { finish("المستلم الظاهر لا يطابق «" + task.plan.recipient + "». لم أعبئ أو أرسل الرسالة؛ اختر المحادثة الصحيحة وأعد الطلب."); return; }
                    if (stage == Stage.VERIFY_BODY) {
                        if (task.plan.body.equals(text(composer.field))) prepareConfirmation(window, composer);
                        else if (clock - stageStarted > 3000) finish("لم يثبت النص المطلوب في المسودة. راجعه يدويًا؛ لم أرسل أو أنشر.");
                        return;
                    }
                    String existing = text(composer.field);
                    if (!existing.isEmpty() && !task.plan.body.equals(existing)) { finish("توجد مسودة مختلفة في التطبيق. احتفظت بها؛ راجعها أو امسحها بنفسك ثم أعد الطلب."); return; }
                    if (task.plan.body.equals(existing)) { prepareConfirmation(window, composer); return; }
                    if (!setText(composer.field, task.plan.body)) { finish("لا يدعم حقل المسودة التعبئة الآمنة. أكملها يدويًا؛ لم أرسل أو أنشر."); return; }
                    transition(Stage.VERIFY_BODY, "أتحقق من النص والمستلم قبل التأكيد النهائي…");
                    return;
                } } finally { if (composer != null && composer.commit != null) composer.commit.recycle(); }
                if (stage == Stage.VERIFY_BODY) { finish("تغيّرت شاشة المسودة أثناء تعبئتها؛ أُلغيت المهمة."); return; }
                if (facebook) findFacebookComposer(nodes);
                else findWhatsAppRecipient(nodes);
            } finally { for (AccessibilityNodeInfo node : nodes) node.recycle(); }
        } catch (RuntimeException error) { finish("تعذّر التحقق من شاشة التطبيق. توقفت المهمة؛ راجع المسودة يدويًا."); }
    }

    private void findFacebookComposer(List<AccessibilityNodeInfo> nodes) {
        if (stage == Stage.WAIT_FACEBOOK_COMPOSER) {
            if (now() - stageStarted > 12000) finish("لم أجد محرر منشور واضحًا. افتح «إنشاء منشور» في فيسبوك ثم أعد الطلب.");
            return;
        }
        List<AccessibilityNodeInfo> candidates = matchingClickables(nodes, node -> facebookOpener(label(node)));
        try {
            if (candidates.size() > 1) { finish("ظهرت عدة أزرار لإنشاء المنشور. افتح المحرر المقصود بنفسك ثم أعد الطلب."); return; }
            if (candidates.size() == 1) {
                if (!navigate(candidates.get(0))) { finish("تعذّر فتح محرر المنشور. افتحه يدويًا ثم أعد الطلب."); return; }
                transition(Stage.WAIT_FACEBOOK_COMPOSER, "أنتظر محرر منشور فيسبوك…");
            } else if (now() - stageStarted > 12000) finish("لم أجد زر إنشاء منشور واضحًا. افتح محرر فيسبوك بنفسك ثم أعد الطلب.");
        } finally { recycle(candidates); }
    }

    private void findWhatsAppRecipient(List<AccessibilityNodeInfo> nodes) {
        if (stage == Stage.WAIT_WHATSAPP_COMPOSER) {
            // A picker button called Send can commit immediately in some WhatsApp versions. Never press it here.
            if (!clickedNext && selectedRecipientVisible(nodes)) {
                List<AccessibilityNodeInfo> next = matchingClickables(nodes, node -> exact(label(node), "Next", "Continue", "التالي", "متابعة"));
                try {
                    if (next.size() == 1) { clickedNext = true; if (!navigate(next.get(0))) { finish("تعذّر فتح مسودة الرسالة. أكمل اختيار المستلم بنفسك."); return; } notBefore = now() + 600; }
                } finally { recycle(next); }
            }
            if (now() - stageStarted > 18000) finish("اختر المستلم الصحيح وافتح مسودة الرسالة بنفسك، ثم أعد الطلب. لم أضغط زر الإرسال في شاشة اختيار المستلمين.");
            return;
        }
        boolean picker = false;
        for (AccessibilityNodeInfo node : nodes) {
            if (exact(label(node), "Send to", "Send to…", "Send to...", "Select contact", "Select a contact", "Select contacts", "إرسال إلى", "اختر جهة اتصال", "اختيار جهة اتصال")
                || (node.isEditable() && searchField(node))) { picker = true; break; }
        }
        if (!picker) {
            if (now() - stageStarted > 10000) finish("لم أجد شاشة اختيار مستلم واضحة. افتح المحادثة الصحيحة بنفسك ثم أعد الطلب.");
            return;
        }
        List<AccessibilityNodeInfo> contacts = matchingClickables(nodes, node -> contactEquals(label(node), task.plan.recipient) && !node.isEditable());
        try {
            if (contacts.size() > 1) { finish("يوجد أكثر من مستلم باسم «" + task.plan.recipient + "». اختر جهة الاتصال الصحيحة يدويًا؛ لن أخمّن المستلم."); return; }
            if (contacts.size() == 1) {
                if (!navigate(contacts.get(0))) { finish("تعذّر اختيار المستلم. افتح المحادثة الصحيحة بنفسك."); return; }
                transition(Stage.WAIT_WHATSAPP_COMPOSER, "اختر المستلم وواصل إلى شاشة المسودة عند الحاجة؛ لن أضغط إرسال هنا.");
                return;
            }
        } finally { recycle(contacts); }
        if (!searched) {
            List<AccessibilityNodeInfo> searches = new ArrayList<>();
            for (AccessibilityNodeInfo node : nodes) if (node.isEditable() && searchField(node)) searches.add(node);
            if (searches.size() > 1) { finish("يوجد أكثر من حقل بحث. اختر المستلم يدويًا ثم أعد الطلب."); return; }
            if (searches.size() == 1) {
                if (!setText(searches.get(0), task.plan.recipient)) { finish("تعذّر البحث الآمن عن المستلم. اختره بنفسك."); return; }
                searched = true; transition(Stage.WAIT_SEARCH, "أبحث محليًا عن مستلم مطابق تمامًا…"); return;
            }
            if (!openedSearch) {
                List<AccessibilityNodeInfo> buttons = matchingClickables(nodes, node -> exact(label(node), "Search", "بحث", "البحث"));
                try {
                    if (buttons.size() == 1) { openedSearch = true; if (!navigate(buttons.get(0))) { finish("تعذّر فتح البحث عن المستلم."); return; } transition(Stage.WAIT_SEARCH, "أفتح البحث المحلي عن المستلم…"); return; }
                } finally { recycle(buttons); }
            }
        }
        if (now() - stageStarted > (searched ? 6500 : 12000)) finish("لم أجد مستلمًا واحدًا مطابقًا للاسم «" + task.plan.recipient + "». اختره يدويًا في واتساب ثم أعد الطلب.");
    }

    private boolean selectedRecipientVisible(List<AccessibilityNodeInfo> nodes) {
        int selected = 0;
        boolean target = false;
        for (AccessibilityNodeInfo node : nodes) if ((node.isSelected() || node.isChecked()) && !node.isEditable()) {
            String label = label(node);
            if (!label.isEmpty()) { selected++; if (contactEquals(label, task.plan.recipient)) target = true; }
        }
        return target && selected == 1;
    }

    private static final class Composer {
        final AccessibilityNodeInfo field;
        final AccessibilityNodeInfo commit;
        final boolean recipientMatches;
        final boolean uniqueCommit;
        Composer(AccessibilityNodeInfo field, AccessibilityNodeInfo commit, boolean recipientMatches, boolean uniqueCommit) {
            this.field = field; this.commit = commit; this.recipientMatches = recipientMatches; this.uniqueCommit = uniqueCommit;
        }
    }
    private Composer composer(MaxAccessibilityService.AppWindow window, List<AccessibilityNodeInfo> nodes, boolean facebook) {
        List<AccessibilityNodeInfo> editors = new ArrayList<>();
        boolean proof = false;
        for (AccessibilityNodeInfo node : nodes) {
            if (facebook && exact(label(node), "Create post", "Create a post", "New post", "إنشاء منشور", "انشاء منشور", "كتابة منشور")) proof = true;
            if (node.isEditable() && !node.isPassword()) {
                if (facebook || messageField(node)) editors.add(node);
                if (facebook && facebookOpener(hint(node))) proof = true;
            }
        }
        if (editors.size() != 1 || (facebook && !proof)) return null;
        AccessibilityNodeInfo field = editors.get(0);
        if (!field.isEnabled()) return null;
        boolean correctRecipient = facebook || recipientHeader(window, nodes, field);
        List<AccessibilityNodeInfo> commits = matchingClickables(nodes, node -> {
            String value = label(node);
            if (facebook) return exact(value, "Post", "نشر", "Share now", "مشاركة الآن", "مشاركة الان");
            return exact(value, "Send", "إرسال", "ارسال") || (idEnds(node, "send") && value.isEmpty());
        });
        try {
            // Keep a separate Node copy alive only through this single inspection. The caller refreshes before use.
            AccessibilityNodeInfo commit = commits.size() == 1 ? AccessibilityNodeInfo.obtain(commits.get(0)) : null;
            return new Composer(field, commit, correctRecipient, commits.size() == 1 && commit.isEnabled());
        } finally { recycle(commits); }
    }

    private boolean recipientHeader(MaxAccessibilityService.AppWindow window, List<AccessibilityNodeInfo> nodes, AccessibilityNodeInfo field) {
        int matches = 0;
        Rect rootBounds = new Rect(); window.root.getBoundsInScreen(rootBounds);
        Rect bounds = new Rect(); field.getBoundsInScreen(bounds);
        for (AccessibilityNodeInfo node : nodes) {
            if (node.isEditable() || !contactEquals(label(node), task.plan.recipient)) continue;
            Rect candidate = new Rect(); node.getBoundsInScreen(candidate);
            boolean headerId = idEnds(node, "conversation_contact_name") || idEnds(node, "conversation_title");
            boolean topTitle = candidate.top >= rootBounds.top && candidate.bottom <= rootBounds.top + rootBounds.height() / 4 && candidate.bottom < bounds.top;
            if (headerId || topTitle) matches++;
        }
        return matches == 1;
    }
    private CommitGuard.Evidence evidence(MaxAccessibilityService.AppWindow window, Composer composer) {
        Rect bounds = new Rect(); composer.field.getBoundsInScreen(bounds);
        String identity = String.valueOf(composer.field.getViewIdResourceName()) + ":" + composer.field.getClassName() + ":" + bounds.flattenToString();
        return new CommitGuard.Evidence(task.packageName, window.id, true, true, true, MaxAccessibilityService.deviceUnlocked(service),
            composer.recipientMatches, composer.uniqueCommit, composer.recipientMatches ? task.plan.recipient : "", text(composer.field), identity);
    }
    private void prepareConfirmation(MaxAccessibilityService.AppWindow window, Composer composer) {
            if (!composer.uniqueCommit) {
                if (now() - stageStarted > 5000) finish("لم أجد زر إرسال أو نشر واحدًا واضحًا في محرر المسودة. أكمل المراجعة يدويًا.");
                return;
            }
            reviewRevision = guard.arm(evidence(window, composer), now());
            if (reviewRevision < 0) { finish("تعذّر التحقق من المسودة والمستلم. لم أرسل أو أنشر."); return; }
            stage = Stage.WAIT_CONFIRM; stageStarted = now(); notBefore = 0;
            String preview = "التطبيق: " + task.label;
            if (!task.plan.recipient.isEmpty()) preview += "\nالمستلم: " + task.plan.recipient;
            preview += "\n\nالنص:\n" + task.plan.body;
            final long revision = reviewRevision;
            overlay.requestConfirmation(preview, task.plan.kind == DevicePlan.Kind.FACEBOOK_POST, () -> confirm(revision));
            owner.progress(task.id, "جهزت المسودة في " + task.label + ". أنتظر تأكيدك على الشاشة قبل الضغط النهائي؛ لم أرسل أو أنشر بعد.");
    }
    private void validateReview(MaxAccessibilityService.AppWindow window, List<AccessibilityNodeInfo> nodes) {
        Composer composer = composer(window, nodes, task.plan.kind == DevicePlan.Kind.FACEBOOK_POST);
        try {
            if (composer == null || !guard.matchesReview(evidence(window, composer), now())) finish("تغيّرت المسودة أو المستلم أو شاشة التطبيق بعد المراجعة. أُلغيت المهمة؛ أعد الطلب لمراجعة جديدة.");
        } finally { if (composer != null && composer.commit != null) composer.commit.recycle(); }
    }
    private void confirm(long revision) {
        if (finished || stage != Stage.WAIT_CONFIRM || revision != reviewRevision) return;
        stage = Stage.COMMITTING;
        // Dismiss the touched overlay before requiring a fresh active/focused application window.
        // Some Android versions temporarily mark a touched overlay as the active window.
        overlay.close();
        handler.postDelayed(() -> commit(revision), 220);
    }
    private void commit(long revision) {
        if (finished || stage != Stage.COMMITTING || revision != reviewRevision) return;
        // No stored node is ever clicked after approval: obtain the application window and selectors again.
        try (MaxAccessibilityService.AppWindow window = service.appWindow(task.packageName)) {
            if (window == null || !MaxAccessibilityService.deviceUnlocked(service)) { finish("تغيّر التطبيق أو قفل الهاتف. أُلغيت الموافقة؛ لم أضغط الخطوة النهائية."); return; }
            List<AccessibilityNodeInfo> nodes = visibleNodes(window.root);
            Composer composer = null;
            try {
                composer = composer(window, nodes, task.plan.kind == DevicePlan.Kind.FACEBOOK_POST);
                if (containsPassword(nodes) || composer == null || !guard.consume(task.id, revision, evidence(window, composer), now())) { finish("لم تعد المسودة أو المستلم يطابقان مراجعتك. أُلغيت الموافقة؛ أعد الطلب."); return; }
                boolean pressed = composer.commit != null && composer.commit.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                boolean facebook = task.plan.kind == DevicePlan.Kind.FACEBOOK_POST;
                if (pressed) finish(facebook ? "ضغطت زر النشر في فيسبوك بعد تأكيدك. تحقّق من ظهور المنشور داخل التطبيق؛ لا أستطيع تأكيد وصوله إلى الخادم." : "ضغطت زر الإرسال إلى «" + task.plan.recipient + "» في واتساب بعد تأكيدك. تحقّق من حالة الرسالة داخل التطبيق؛ لا أستطيع تأكيد تسليمها.");
                else finish("لم يقبل التطبيق الضغط على الزر النهائي. لن أعيد المحاولة تلقائيًا؛ راجع المسودة داخل التطبيق.");
            } finally { if (composer != null && composer.commit != null) composer.commit.recycle(); for (AccessibilityNodeInfo node : nodes) node.recycle(); }
        } catch (RuntimeException error) { finish("تعذّر إكمال الضغط النهائي. لن أعيد المحاولة؛ تحقّق من حالة المسودة داخل التطبيق."); }
    }

    private boolean navigate(AccessibilityNodeInfo node) { return ++navigationActions <= 6 && node.isEnabled() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK); }
    private static boolean setText(AccessibilityNodeInfo node, String value) {
        if (!node.isEditable() || node.isPassword() || !node.isEnabled()) return false;
        Bundle arguments = new Bundle(); arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
    }
    private static List<AccessibilityNodeInfo> visibleNodes(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> result = new ArrayList<>(); Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(AccessibilityNodeInfo.obtain(root)); int visited = 0;
        while (!queue.isEmpty() && visited++ < 700) {
            AccessibilityNodeInfo node = queue.remove();
            if (!node.isVisibleToUser() || (node.getPackageName() != null && root.getPackageName() != null && !root.getPackageName().toString().contentEquals(node.getPackageName()))) { node.recycle(); continue; }
            result.add(node);
            if (node.isPassword()) continue;
            for (int i = 0; i < node.getChildCount() && queue.size() + result.size() < 1000; i++) { AccessibilityNodeInfo child = node.getChild(i); if (child != null) queue.add(child); }
        }
        while (!queue.isEmpty()) queue.remove().recycle();
        return result;
    }
    private interface Selector { boolean matches(AccessibilityNodeInfo node); }
    private static List<AccessibilityNodeInfo> matchingClickables(List<AccessibilityNodeInfo> nodes, Selector selector) {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        for (AccessibilityNodeInfo node : nodes) if (!node.isPassword() && selector.matches(node)) {
            AccessibilityNodeInfo candidate = AccessibilityNodeInfo.obtain(node);
            int depth = 0;
            while (candidate != null && !candidate.isClickable() && depth++ < 3) {
                AccessibilityNodeInfo parent = candidate.getParent(); candidate.recycle(); candidate = parent;
            }
            if (candidate == null) continue;
            if (!candidate.isClickable() || !candidate.isVisibleToUser() || candidate.isPassword()) { candidate.recycle(); continue; }
            boolean duplicate = false; for (AccessibilityNodeInfo prior : result) if (prior.equals(candidate)) { duplicate = true; break; }
            if (!duplicate) result.add(candidate); else candidate.recycle();
        }
        return result;
    }
    private static void recycle(List<AccessibilityNodeInfo> nodes) { for (AccessibilityNodeInfo node : nodes) node.recycle(); }
    private static boolean containsPassword(List<AccessibilityNodeInfo> nodes) { for (AccessibilityNodeInfo node : nodes) if (node.isPassword()) return true; return false; }
    private static String text(AccessibilityNodeInfo node) { return node.isShowingHintText() || node.getText() == null ? "" : node.getText().toString(); }
    private static String label(AccessibilityNodeInfo node) {
        CharSequence value = node.getText(); if (value == null || value.toString().trim().isEmpty()) value = node.getContentDescription();
        return value == null ? "" : value.toString().trim();
    }
    private static String hint(AccessibilityNodeInfo node) { return node.getHintText() == null ? "" : node.getHintText().toString(); }
    private static boolean exact(String value, String... allowed) { for (String item : allowed) if (AppRegistry.normalize(value).equals(AppRegistry.normalize(item))) return true; return false; }
    private static boolean contactEquals(String first, String second) { return !first.isEmpty() && AppRegistry.normalize(first).equals(AppRegistry.normalize(second)); }
    private static boolean facebookOpener(String value) {
        String normalized = AppRegistry.normalize(value);
        return exact(value, "بم تفكر؟", "بم تفكر?", "بم تفكر", "بم تفكر الآن؟", "ماذا يدور في ذهنك؟", "Create post", "إنشاء منشور", "انشاء منشور", "Write something…", "Write something...")
            || normalized.startsWith("what's on your mind") || normalized.startsWith("what’s on your mind");
    }
    private static boolean idEnds(AccessibilityNodeInfo node, String id) { String name = node.getViewIdResourceName(); return name != null && name.endsWith(":id/" + id); }
    private static boolean messageField(AccessibilityNodeInfo node) {
        return idEnds(node, "entry") || idEnds(node, "message_text") || idEnds(node, "message_input")
            || exact(hint(node), "Message", "Type a message", "رسالة", "اكتب رسالة", "اكتب رسالة…", "اكتب رسالة...");
    }
    private static boolean searchField(AccessibilityNodeInfo node) { return idEnds(node, "search_src_text") || idEnds(node, "search") || exact(hint(node), "Search", "Search…", "Search...", "بحث", "البحث"); }
}
