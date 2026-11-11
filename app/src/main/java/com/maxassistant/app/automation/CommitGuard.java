package com.maxassistant.app.automation;

/** Pure state machine for a one-shot confirmation. Screen evidence is always refreshed by the controller. */
final class CommitGuard {
    static final long MAX_DURATION_MS = 120_000L;
    static final class Evidence {
        final String packageName, recipient, body, fieldIdentity;
        final int windowId;
        final boolean application, active, focused, unlocked, composer, uniqueCommit;
        Evidence(String packageName, int windowId, boolean application, boolean active, boolean focused,
                 boolean unlocked, boolean composer, boolean uniqueCommit, String recipient, String body, String fieldIdentity) {
            this.packageName = packageName; this.windowId = windowId; this.application = application;
            this.active = active; this.focused = focused; this.unlocked = unlocked; this.composer = composer;
            this.uniqueCommit = uniqueCommit; this.recipient = recipient; this.body = body; this.fieldIdentity = fieldIdentity;
        }
    }
    private final String taskId, expectedPackage, expectedRecipient, expectedBody;
    private final long deadline;
    private boolean stopped, attempted, armed;
    private int windowId;
    private String fieldIdentity;
    private long revision;

    CommitGuard(String taskId, String expectedPackage, String expectedRecipient, String expectedBody, long startedAt) {
        this.taskId = taskId; this.expectedPackage = expectedPackage; this.expectedRecipient = expectedRecipient;
        this.expectedBody = expectedBody; this.deadline = startedAt + MAX_DURATION_MS;
    }
    boolean windowAllowed(Evidence evidence, long now) {
        return !stopped && !attempted && now < deadline && evidence != null && evidence.application && evidence.active && evidence.focused
            && evidence.unlocked && expectedPackage.equals(evidence.packageName);
    }
    private boolean validDraft(Evidence evidence, long now) {
        return windowAllowed(evidence, now) && evidence.composer && evidence.uniqueCommit
            && expectedRecipient.equals(evidence.recipient) && expectedBody.equals(evidence.body)
            && evidence.fieldIdentity != null && !evidence.fieldIdentity.isEmpty();
    }
    long arm(Evidence evidence, long now) {
        if (!validDraft(evidence, now)) return -1;
        if (armed) return revision;
        windowId = evidence.windowId; fieldIdentity = evidence.fieldIdentity; armed = true;
        return ++revision;
    }
    boolean matchesReview(Evidence evidence, long now) {
        return armed && validDraft(evidence, now) && windowId == evidence.windowId && fieldIdentity.equals(evidence.fieldIdentity);
    }
    boolean consume(String task, long reviewedRevision, Evidence fresh, long now) {
        if (!taskId.equals(task) || !armed || revision != reviewedRevision || stopped || attempted) return false;
        // Any current review mismatch invalidates this confirmation, even if the user restores the draft later.
        if (!matchesReview(fresh, now)) { stopped = true; return false; }
        attempted = true; armed = false;
        return true;
    }
    boolean expired(long now) { return now >= deadline; }
    void cancel() { stopped = true; armed = false; }
}
