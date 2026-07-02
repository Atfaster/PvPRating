package dev.pvprating.utils;

import dev.pvprating.PvPRatingMod;
import dev.pvprating.configs.Config;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RatingAnomalyDetector {
    private static final int MAX_PLAYER_WINDOWS = 4096;
    private static final int MAX_PAIR_WINDOWS = 4096;
    private static final int MAX_ALERT_RECORDS = 4096;
    private static final int MAX_EVENTS_PER_WINDOW = 256;

    private static final Map<UUID, PlayerWindow> playerWindows = new LinkedHashMap<>();
    private static final Map<PairKey, PairWindow> pairWindows = new LinkedHashMap<>();
    private static final Map<String, Long> lastAlertTimes = new LinkedHashMap<>();

    public static synchronized void analyzeKill(
            ServerPlayer killer,
            ServerPlayer victim,
            double killerPreviousRating,
            double killerCurrentRating,
            boolean killerRatingChanged,
            double victimPreviousRating,
            double victimCurrentRating,
            boolean victimRatingChanged
    ) {
        if (!Config.RatingAnomalyDetectionEnabled.get()) return;

        long nowMillis = java.lang.System.currentTimeMillis();
        analyzePlayerChange(
                "kill_gain",
                killer.getGameProfile().getName(),
                killer.getUUID(),
                victim.getGameProfile().getName(),
                killerPreviousRating,
                killerCurrentRating,
                nowMillis
        );
        analyzePlayerChange(
                "kill_loss",
                victim.getGameProfile().getName(),
                victim.getUUID(),
                killer.getGameProfile().getName(),
                victimPreviousRating,
                victimCurrentRating,
                nowMillis
        );

        if (killerRatingChanged || victimRatingChanged) {
            analyzePair(killer, victim, nowMillis);
        }
    }

    public static synchronized void analyzeAdminChange(
            CommandSourceStack source,
            String action,
            String targetName,
            UUID targetUuid,
            double previousRating,
            double currentRating,
            Double commandAmount
    ) {
        if (!Config.RatingAnomalyDetectionEnabled.get() || !Config.RatingAnomalyTrackAdminChanges.get()) return;

        analyzePlayerChange(
                "admin_" + action,
                targetName,
                targetUuid,
                source.getTextName(),
                previousRating,
                currentRating,
                java.lang.System.currentTimeMillis()
        );
    }

    private static void analyzePlayerChange(
            String sourceType,
            String playerName,
            UUID playerUuid,
            String counterpartyName,
            double previousRating,
            double currentRating,
            long nowMillis
    ) {
        double delta = currentRating - previousRating;
        boolean ratingChanged = Double.compare(delta, 0.0) != 0;
        List<String> reasons = new ArrayList<>();

        if (!Double.isFinite(previousRating) || !Double.isFinite(currentRating) || !Double.isFinite(delta)) {
            reasons.add("non_finite_rating_value");
        }

        double singleDeltaThreshold = Config.RatingAnomalyMaxSingleDelta.get();
        if (singleDeltaThreshold > 0.0 && Math.abs(delta) >= singleDeltaThreshold) {
            reasons.add("large_single_delta");
        }

        PlayerWindow window = playerWindows.computeIfAbsent(playerUuid, uuid -> new PlayerWindow());
        cleanupPlayerWindow(window, nowMillis);
        if (ratingChanged || !reasons.isEmpty()) {
            window.events.addLast(new RatingChange(nowMillis, delta));
            trimPlayerWindow(window);
        }

        WindowStats stats = windowStats(window);
        double gainThreshold = Config.RatingAnomalyMaxGainInWindow.get();
        if (gainThreshold > 0.0 && stats.gain >= gainThreshold) {
            reasons.add("rapid_rating_gain");
        }

        double lossThreshold = Config.RatingAnomalyMaxLossInWindow.get();
        if (lossThreshold > 0.0 && stats.loss >= lossThreshold) {
            reasons.add("rapid_rating_loss");
        }

        int maxChanges = Config.RatingAnomalyMaxChangesInWindow.get();
        if (maxChanges > 0 && stats.count >= maxChanges) {
            reasons.add("high_change_frequency");
        }

        trimPlayerWindows();
        alertPlayerIfNeeded(
                sourceType,
                playerName,
                playerUuid,
                counterpartyName,
                previousRating,
                currentRating,
                delta,
                stats,
                reasons,
                nowMillis
        );
    }

    private static void analyzePair(ServerPlayer killer, ServerPlayer victim, long nowMillis) {
        int maxPairChanges = Config.RatingAnomalyMaxPairChangesInWindow.get();
        if (maxPairChanges <= 0) return;

        PairKey pairKey = new PairKey(killer.getUUID(), victim.getUUID());
        PairWindow window = pairWindows.computeIfAbsent(pairKey, key -> new PairWindow());
        cleanupPairWindow(window, nowMillis);
        window.eventTimes.addLast(nowMillis);
        trimPairWindow(window);
        trimPairWindows();

        if (window.eventTimes.size() < maxPairChanges) return;

        List<String> reasons = List.of("pair_farming_pattern");
        List<String> unsuppressedReasons = unsuppressedReasons("pair", pairKey.toString(), reasons, nowMillis);
        if (unsuppressedReasons.isEmpty() || !Config.RatingAnomalyConsoleAlertsEnabled.get()) return;

        PvPRatingMod.LOGGER.warn(
                "PvPRating anomaly: reasons={} source=kill_pair killer={}({}) victim={}({}) pairChangesInWindow={} windowSeconds={}",
                unsuppressedReasons,
                killer.getGameProfile().getName(),
                killer.getUUID(),
                victim.getGameProfile().getName(),
                victim.getUUID(),
                window.eventTimes.size(),
                Config.RatingAnomalyWindowSeconds.get()
        );
    }

    private static void alertPlayerIfNeeded(
            String sourceType,
            String playerName,
            UUID playerUuid,
            String counterpartyName,
            double previousRating,
            double currentRating,
            double delta,
            WindowStats stats,
            List<String> reasons,
            long nowMillis
    ) {
        if (reasons.isEmpty() || !Config.RatingAnomalyConsoleAlertsEnabled.get()) return;

        List<String> unsuppressedReasons = unsuppressedReasons("player", playerUuid.toString(), reasons, nowMillis);
        if (unsuppressedReasons.isEmpty()) return;

        String message = "PvPRating anomaly: reasons={} source={} player={}({}) counterparty={} previousRating={} currentRating={} delta={} windowGain={} windowLoss={} changesInWindow={} windowSeconds={}";
        if (unsuppressedReasons.contains("non_finite_rating_value")) {
            PvPRatingMod.LOGGER.error(
                    message,
                    unsuppressedReasons,
                    sourceType,
                    playerName,
                    playerUuid,
                    counterpartyName,
                    previousRating,
                    currentRating,
                    delta,
                    stats.gain,
                    stats.loss,
                    stats.count,
                    Config.RatingAnomalyWindowSeconds.get()
            );
        } else {
            PvPRatingMod.LOGGER.warn(
                    message,
                    unsuppressedReasons,
                    sourceType,
                    playerName,
                    playerUuid,
                    counterpartyName,
                    previousRating,
                    currentRating,
                    delta,
                    stats.gain,
                    stats.loss,
                    stats.count,
                    Config.RatingAnomalyWindowSeconds.get()
            );
        }
    }

    private static List<String> unsuppressedReasons(String scope, String id, List<String> reasons, long nowMillis) {
        List<String> unsuppressed = new ArrayList<>();

        for (String reason : reasons) {
            String alertKey = scope + ":" + id + ":" + reason;
            if (shouldAlert(alertKey, nowMillis)) {
                unsuppressed.add(reason);
            }
        }

        return unsuppressed;
    }

    private static boolean shouldAlert(String alertKey, long nowMillis) {
        cleanupAlertTimes(nowMillis);

        int cooldownSeconds = Config.RatingAnomalyAlertCooldownSeconds.get();
        if (cooldownSeconds <= 0) {
            lastAlertTimes.put(alertKey, nowMillis);
            trimAlertTimes();
            return true;
        }

        Long lastAlertMillis = lastAlertTimes.get(alertKey);
        if (lastAlertMillis != null && nowMillis - lastAlertMillis < cooldownSeconds * 1000L) return false;

        lastAlertTimes.put(alertKey, nowMillis);
        trimAlertTimes();
        return true;
    }

    private static void cleanupPlayerWindow(PlayerWindow window, long nowMillis) {
        long oldestAllowedMillis = nowMillis - Config.RatingAnomalyWindowSeconds.get() * 1000L;
        while (!window.events.isEmpty() && window.events.peekFirst().timeMillis < oldestAllowedMillis) {
            window.events.removeFirst();
        }
    }

    private static void cleanupPairWindow(PairWindow window, long nowMillis) {
        long oldestAllowedMillis = nowMillis - Config.RatingAnomalyWindowSeconds.get() * 1000L;
        while (!window.eventTimes.isEmpty() && window.eventTimes.peekFirst() < oldestAllowedMillis) {
            window.eventTimes.removeFirst();
        }
    }

    private static void cleanupAlertTimes(long nowMillis) {
        int cooldownSeconds = Config.RatingAnomalyAlertCooldownSeconds.get();
        if (cooldownSeconds <= 0 || lastAlertTimes.isEmpty()) return;

        long oldestAllowedMillis = nowMillis - cooldownSeconds * 1000L * 2L;
        Iterator<Map.Entry<String, Long>> iterator = lastAlertTimes.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() < oldestAllowedMillis) {
                iterator.remove();
            }
        }
    }

    private static WindowStats windowStats(PlayerWindow window) {
        double gain = 0.0;
        double loss = 0.0;
        int count = 0;

        for (RatingChange event : window.events) {
            if (event.delta > 0.0) gain += event.delta;
            if (event.delta < 0.0) loss += Math.abs(event.delta);
            count++;
        }

        return new WindowStats(gain, loss, count);
    }

    private static void trimPlayerWindow(PlayerWindow window) {
        while (window.events.size() > MAX_EVENTS_PER_WINDOW) {
            window.events.removeFirst();
        }
    }

    private static void trimPairWindow(PairWindow window) {
        while (window.eventTimes.size() > MAX_EVENTS_PER_WINDOW) {
            window.eventTimes.removeFirst();
        }
    }

    private static void trimPlayerWindows() {
        while (playerWindows.size() > MAX_PLAYER_WINDOWS) {
            Iterator<Map.Entry<UUID, PlayerWindow>> iterator = playerWindows.entrySet().iterator();
            if (!iterator.hasNext()) return;
            iterator.next();
            iterator.remove();
        }
    }

    private static void trimPairWindows() {
        while (pairWindows.size() > MAX_PAIR_WINDOWS) {
            Iterator<Map.Entry<PairKey, PairWindow>> iterator = pairWindows.entrySet().iterator();
            if (!iterator.hasNext()) return;
            iterator.next();
            iterator.remove();
        }
    }

    private static void trimAlertTimes() {
        while (lastAlertTimes.size() > MAX_ALERT_RECORDS) {
            Iterator<Map.Entry<String, Long>> iterator = lastAlertTimes.entrySet().iterator();
            if (!iterator.hasNext()) return;
            iterator.next();
            iterator.remove();
        }
    }

    private record RatingChange(long timeMillis, double delta) {
    }

    private record WindowStats(double gain, double loss, int count) {
    }

    private record PairKey(UUID killerUuid, UUID victimUuid) {
    }

    private static class PlayerWindow {
        private final ArrayDeque<RatingChange> events = new ArrayDeque<>();
    }

    private static class PairWindow {
        private final ArrayDeque<Long> eventTimes = new ArrayDeque<>();
    }
}
