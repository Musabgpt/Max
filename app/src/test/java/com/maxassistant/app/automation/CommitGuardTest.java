package com.maxassistant.app.automation;

import org.junit.Test;
import static org.junit.Assert.*;

public final class CommitGuardTest {
    private static final long START = 1_000;
    private CommitGuard guard() { return new CommitGuard("task-1", "com.whatsapp", "مروة", "مرحبا", START); }
    private CommitGuard.Evidence valid() { return evidence("com.whatsapp", 7, true, true, true, true, true, true, "مروة", "مرحبا", "entry:bounds"); }
    private CommitGuard.Evidence evidence(String pkg, int window, boolean app, boolean active, boolean focus, boolean unlocked, boolean composer, boolean unique, String recipient, String body, String field) {
        return new CommitGuard.Evidence(pkg, window, app, active, focus, unlocked, composer, unique, recipient, body, field);
    }
    @Test public void cannotCommitUntilFreshDraftHasBeenReviewed() {
        CommitGuard guard = guard(); assertFalse(guard.consume("task-1", 1, valid(), START + 10));
        long review = guard.arm(valid(), START + 20); assertTrue(review > 0);
        assertTrue(guard.consume("task-1", review, valid(), START + 30));
        assertFalse(guard.consume("task-1", review, valid(), START + 31));
        assertEquals(-1, guard.arm(valid(), START + 32));
    }
    @Test public void wrongTaskAndStaleCallbackCannotUseCurrentConsent() {
        CommitGuard guard = guard(); long review = guard.arm(valid(), START + 1);
        assertFalse(guard.consume("old-task", review, valid(), START + 2));
        assertFalse(guard.consume("task-1", review + 1, valid(), START + 3));
        assertTrue(guard.consume("task-1", review, valid(), START + 4));
    }
    @Test public void editedBodyWrongRecipientAndReplacedComposerInvalidateConsent() {
        CommitGuard.Evidence[] changed = {
            evidence("com.whatsapp", 7, true,true,true,true,true,true,"مروة","changed","entry:bounds"),
            evidence("com.whatsapp", 7, true,true,true,true,true,true,"علي","مرحبا","entry:bounds"),
            evidence("com.whatsapp", 8, true,true,true,true,true,true,"مروة","مرحبا","entry:bounds"),
            evidence("com.whatsapp", 7, true,true,true,true,true,true,"مروة","مرحبا","new-entry"),
            evidence("com.whatsapp", 7, true,true,true,true,false,true,"مروة","مرحبا","entry:bounds"),
            evidence("com.whatsapp", 7, true,true,true,true,true,false,"مروة","مرحبا","entry:bounds")
        };
        for (CommitGuard.Evidence fresh : changed) {
            CommitGuard guard = guard(); long review = guard.arm(valid(), START + 1);
            assertFalse(guard.matchesReview(fresh, START + 2));
            assertFalse(guard.consume("task-1", review, fresh, START + 3));
            assertFalse(guard.consume("task-1", review, valid(), START + 4));
        }
    }
    @Test public void lockWrongPackageOverlayInactiveAndUnfocusedWindowsAreNeverTargets() {
        CommitGuard.Evidence[] invalid = {
            evidence("com.facebook.katana",7,true,true,true,true,true,true,"مروة","مرحبا","entry:bounds"),
            evidence("com.whatsapp",7,false,true,true,true,true,true,"مروة","مرحبا","entry:bounds"),
            evidence("com.whatsapp",7,true,false,true,true,true,true,"مروة","مرحبا","entry:bounds"),
            evidence("com.whatsapp",7,true,true,false,true,true,true,"مروة","مرحبا","entry:bounds"),
            evidence("com.whatsapp",7,true,true,true,false,true,true,"مروة","مرحبا","entry:bounds")
        };
        for (CommitGuard.Evidence fresh : invalid) {
            CommitGuard guard = guard(); assertFalse(guard.windowAllowed(fresh, START + 1));
            assertEquals(-1, guard.arm(fresh, START + 2));
            long review = guard.arm(valid(), START + 3); assertFalse(guard.consume("task-1", review, fresh, START + 4));
        }
    }
    @Test public void cancellationAndTimeoutNeverResumeACommit() {
        CommitGuard cancelled = guard(); long review = cancelled.arm(valid(), START + 1); cancelled.cancel();
        assertFalse(cancelled.consume("task-1", review, valid(), START + 2));
        CommitGuard expired = guard(); long olderReview = expired.arm(valid(), START + 1);
        assertTrue(expired.expired(START + 120_000));
        assertFalse(expired.consume("task-1", olderReview, valid(), START + 120_000));
        assertEquals(-1, expired.arm(valid(), START + 120_001));
    }
}
