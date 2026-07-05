package dev.pvprating.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpawnKillProtectionTracker {
    static final int DEFAULT_MAX_PAIR_STATES_PER_KILLER = 512;
    static final long DEFAULT_CLEANUP_INTERVAL_MILLIS = 60_000L;

    private final int maxPairStatesPerKiller;
    private final long cleanupIntervalMillis;
    private final Map<UUID, Map<UUID, PairKillState>> pairKillStates = new HashMap<>();
    private long nextPairStateCleanupMillis;

    public SpawnKillProtectionTracker() {
        this(DEFAULT_MAX_PAIR_STATES_PER_KILLER, DEFAULT_CLEANUP_INTERVAL_MILLIS);
    }

    SpawnKillProtectionTracker(int maxPairStatesPerKiller, long cleanupIntervalMillis) {
        this.maxPairStatesPerKiller = Math.max(1, maxPairStatesPerKiller);
        this.cleanupIntervalMillis = Math.max(1L, cleanupIntervalMillis);
    }

    public KillDecision recordKill(
            UUID killerUuid,
            UUID victimUuid,
            long nowMillis,
            int baseProtectionSeconds,
            int maxProtectionSeconds,
            double cooldownMultiplier,
            boolean victimReady
    ) {
        cleanup(nowMillis);

        Map<UUID, PairKillState> victimKillStates = pairKillStates.computeIfAbsent(killerUuid, uuid -> new HashMap<>());
        PairKillState state = victimKillStates.get(victimUuid);

        if (state == null) {
            victimKillStates.put(victimUuid, PairKillState.initial(nowMillis, baseProtectionSeconds));
            trim(victimKillStates);
            return KillDecision.allowed(0L, baseProtectionSeconds, baseProtectionSeconds, 0);
        }

        long elapsedMillis = nowMillis - state.lastKillTimeMillis();
        if (elapsedMillis >= state.currentCooldownSeconds() * 1000L || victimReady) {
            victimKillStates.put(victimUuid, PairKillState.initial(nowMillis, baseProtectionSeconds));
            trim(victimKillStates);
            return KillDecision.allowed(elapsedMillis, state.currentCooldownSeconds(), baseProtectionSeconds, 0);
        }

        PairKillState escalatedState = state.escalate(nowMillis, baseProtectionSeconds, maxProtectionSeconds, cooldownMultiplier);
        victimKillStates.put(victimUuid, escalatedState);
        trim(victimKillStates);
        return KillDecision.blocked(
                elapsedMillis,
                state.currentCooldownSeconds(),
                escalatedState.currentCooldownSeconds(),
                escalatedState.violations()
        );
    }

    public int reverseViolations(UUID killerUuid, UUID victimUuid) {
        Map<UUID, PairKillState> reverseStates = pairKillStates.get(victimUuid);
        if (reverseStates == null) return 0;

        PairKillState reverseState = reverseStates.get(killerUuid);
        return reverseState == null ? 0 : reverseState.violations();
    }

    public void clearReverseAttemptPenalty(UUID killerUuid, UUID victimUuid) {
        Map<UUID, PairKillState> reverseStates = pairKillStates.get(victimUuid);
        if (reverseStates == null) return;

        reverseStates.remove(killerUuid);
        if (reverseStates.isEmpty()) pairKillStates.remove(victimUuid);
    }

    int stateCount(UUID killerUuid) {
        Map<UUID, PairKillState> victimKillStates = pairKillStates.get(killerUuid);
        return victimKillStates == null ? 0 : victimKillStates.size();
    }

    boolean hasState(UUID killerUuid, UUID victimUuid) {
        Map<UUID, PairKillState> victimKillStates = pairKillStates.get(killerUuid);
        return victimKillStates != null && victimKillStates.containsKey(victimUuid);
    }

    void cleanup(long nowMillis) {
        if (nowMillis < nextPairStateCleanupMillis) return;
        nextPairStateCleanupMillis = nowMillis + cleanupIntervalMillis;

        pairKillStates.entrySet().removeIf(killerEntry -> {
            Map<UUID, PairKillState> victimStates = killerEntry.getValue();
            victimStates.entrySet().removeIf(victimEntry -> victimEntry.getValue().isExpired(nowMillis, cleanupIntervalMillis));
            return victimStates.isEmpty();
        });
    }

    private void trim(Map<UUID, PairKillState> victimKillStates) {
        while (victimKillStates.size() > maxPairStatesPerKiller) {
            UUID oldestVictimUuid = null;
            long oldestKillTimeMillis = Long.MAX_VALUE;

            for (Map.Entry<UUID, PairKillState> entry : victimKillStates.entrySet()) {
                long lastKillTimeMillis = entry.getValue().lastKillTimeMillis();
                if (lastKillTimeMillis < oldestKillTimeMillis) {
                    oldestKillTimeMillis = lastKillTimeMillis;
                    oldestVictimUuid = entry.getKey();
                }
            }

            if (oldestVictimUuid == null) return;
            victimKillStates.remove(oldestVictimUuid);
        }
    }

    public record KillDecision(
            boolean ratingAllowed,
            long elapsedMillis,
            int requiredSeconds,
            int nextRequiredSeconds,
            int violations
    ) {
        private static KillDecision allowed(long elapsedMillis, int requiredSeconds, int nextRequiredSeconds, int violations) {
            return new KillDecision(true, elapsedMillis, requiredSeconds, nextRequiredSeconds, violations);
        }

        private static KillDecision blocked(long elapsedMillis, int requiredSeconds, int nextRequiredSeconds, int violations) {
            return new KillDecision(false, elapsedMillis, requiredSeconds, nextRequiredSeconds, violations);
        }
    }

    private record PairKillState(long lastKillTimeMillis, int currentCooldownSeconds, int violations) {
        private static PairKillState initial(long nowMillis, int protectionSeconds) {
            return new PairKillState(nowMillis, protectionSeconds, 0);
        }

        private PairKillState escalate(long nowMillis, int baseProtectionSeconds, int maxProtectionSeconds, double multiplier) {
            int maxSeconds = maxProtectionSeconds <= 0 ? Integer.MAX_VALUE : maxProtectionSeconds;
            double safeMultiplier = Double.isFinite(multiplier) ? Math.max(1.0, multiplier) : 1.0;
            double multipliedCooldown = currentCooldownSeconds * safeMultiplier;
            int nextCooldownSeconds = (int) Math.min(maxSeconds, Math.max(baseProtectionSeconds, Math.ceil(multipliedCooldown)));
            return new PairKillState(nowMillis, nextCooldownSeconds, violations + 1);
        }

        private boolean isExpired(long nowMillis, long cleanupIntervalMillis) {
            long cooldownMillis = Math.max(1L, currentCooldownSeconds) * 1000L;
            long ttlMillis = Math.max(cleanupIntervalMillis, cooldownMillis * 2L);
            return nowMillis - lastKillTimeMillis >= ttlMillis;
        }
    }
}
