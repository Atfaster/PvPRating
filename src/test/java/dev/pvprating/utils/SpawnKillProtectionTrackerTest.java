package dev.pvprating.utils;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnKillProtectionTrackerTest {
    private static final UUID KILLER = new UUID(0L, 1L);
    private static final UUID VICTIM = new UUID(0L, 2L);

    @Test
    void allowsFirstKillAndBlocksRepeatBeforeCooldown() {
        SpawnKillProtectionTracker tracker = new SpawnKillProtectionTracker();

        SpawnKillProtectionTracker.KillDecision firstKill = tracker.recordKill(
                KILLER, VICTIM, 1_000L, 60, 300, 2.0, false
        );
        SpawnKillProtectionTracker.KillDecision repeatKill = tracker.recordKill(
                KILLER, VICTIM, 30_000L, 60, 300, 2.0, false
        );

        assertTrue(firstKill.ratingAllowed());
        assertFalse(repeatKill.ratingAllowed());
        assertEquals(29L, repeatKill.elapsedMillis() / 1000L);
        assertEquals(60, repeatKill.requiredSeconds());
        assertEquals(120, repeatKill.nextRequiredSeconds());
        assertEquals(1, repeatKill.violations());
    }

    @Test
    void allowsRepeatAfterCurrentCooldown() {
        SpawnKillProtectionTracker tracker = new SpawnKillProtectionTracker();

        tracker.recordKill(KILLER, VICTIM, 0L, 60, 300, 2.0, false);
        SpawnKillProtectionTracker.KillDecision repeatKill = tracker.recordKill(
                KILLER, VICTIM, 60_000L, 60, 300, 2.0, false
        );

        assertTrue(repeatKill.ratingAllowed());
        assertEquals(60, repeatKill.elapsedMillis() / 1000L);
        assertEquals(60, repeatKill.requiredSeconds());
    }

    @Test
    void allowsRepeatWhenVictimIsCombatReady() {
        SpawnKillProtectionTracker tracker = new SpawnKillProtectionTracker();

        tracker.recordKill(KILLER, VICTIM, 0L, 60, 300, 2.0, false);
        SpawnKillProtectionTracker.KillDecision repeatKill = tracker.recordKill(
                KILLER, VICTIM, 5_000L, 60, 300, 2.0, true
        );

        assertTrue(repeatKill.ratingAllowed());
        assertEquals(5, repeatKill.elapsedMillis() / 1000L);
    }

    @Test
    void escalatesCooldownUpToConfiguredMaximum() {
        SpawnKillProtectionTracker tracker = new SpawnKillProtectionTracker();

        tracker.recordKill(KILLER, VICTIM, 0L, 60, 150, 2.0, false);
        SpawnKillProtectionTracker.KillDecision firstBlocked = tracker.recordKill(
                KILLER, VICTIM, 1_000L, 60, 150, 2.0, false
        );
        SpawnKillProtectionTracker.KillDecision secondBlocked = tracker.recordKill(
                KILLER, VICTIM, 2_000L, 60, 150, 2.0, false
        );

        assertFalse(firstBlocked.ratingAllowed());
        assertEquals(120, firstBlocked.nextRequiredSeconds());
        assertFalse(secondBlocked.ratingAllowed());
        assertEquals(150, secondBlocked.nextRequiredSeconds());
        assertEquals(2, secondBlocked.violations());
    }

    @Test
    void exposesAndClearsReverseViolations() {
        SpawnKillProtectionTracker tracker = new SpawnKillProtectionTracker();

        tracker.recordKill(KILLER, VICTIM, 0L, 60, 300, 2.0, false);
        tracker.recordKill(KILLER, VICTIM, 1_000L, 60, 300, 2.0, false);

        assertEquals(1, tracker.reverseViolations(VICTIM, KILLER));

        tracker.clearReverseAttemptPenalty(VICTIM, KILLER);

        assertEquals(0, tracker.reverseViolations(VICTIM, KILLER));
    }

    @Test
    void cleanupRemovesExpiredStates() {
        SpawnKillProtectionTracker tracker = new SpawnKillProtectionTracker(512, 10_000L);

        tracker.recordKill(KILLER, VICTIM, 0L, 5, 300, 2.0, false);
        tracker.cleanup(10_000L);

        assertEquals(0, tracker.stateCount(KILLER));
    }

    @Test
    void trimKeepsStateCountUnderConfiguredCap() {
        SpawnKillProtectionTracker tracker = new SpawnKillProtectionTracker(2, 60_000L);
        UUID firstVictim = new UUID(0L, 10L);
        UUID secondVictim = new UUID(0L, 11L);
        UUID thirdVictim = new UUID(0L, 12L);

        tracker.recordKill(KILLER, firstVictim, 1_000L, 60, 300, 2.0, false);
        tracker.recordKill(KILLER, secondVictim, 2_000L, 60, 300, 2.0, false);
        tracker.recordKill(KILLER, thirdVictim, 3_000L, 60, 300, 2.0, false);

        assertEquals(2, tracker.stateCount(KILLER));
        assertFalse(tracker.hasState(KILLER, firstVictim));
        assertTrue(tracker.hasState(KILLER, secondVictim));
        assertTrue(tracker.hasState(KILLER, thirdVictim));
    }
}
