package dev.pvprating.utils;

import dev.pvprating.PvPRatingMod;
import dev.pvprating.configs.Config;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.UUID;

public class RatingAuditLogger {
    private static final String AUDIT_FILE_NAME = "pvprating-audit.log";
    private static final int RATE_LIMIT_RECORD_LIMIT = 2048;
    private static final Map<String, RateLimitRecord> rateLimitRecords = new LinkedHashMap<>();

    public static void logRatingKill(
            ServerPlayer killer,
            ServerPlayer victim,
            double killerPreviousRating,
            double killerCurrentRating,
            boolean killerRatingChanged,
            boolean killerRatingFrozen,
            double victimPreviousRating,
            double victimCurrentRating,
            boolean victimRatingChanged,
            boolean victimRatingFrozen
    ) {
        if (!Config.AuditLogAppliedKills.get()) return;

        double killerDelta = killerCurrentRating - killerPreviousRating;
        double victimDelta = victimCurrentRating - victimPreviousRating;
        List<String> suspiciousReasons = suspiciousRatingReasons(killerDelta, victimDelta, killerCurrentRating, victimCurrentRating);

        Map<String, Object> fields = baseKillFields(killer, victim);
        fields.put("outcome", killOutcome(killerRatingChanged, victimRatingChanged, killerRatingFrozen, victimRatingFrozen));
        fields.put("killerPreviousRating", killerPreviousRating);
        fields.put("killerCurrentRating", killerCurrentRating);
        fields.put("killerDelta", killerDelta);
        fields.put("killerRatingChanged", killerRatingChanged);
        fields.put("killerRatingFrozen", killerRatingFrozen);
        fields.put("victimPreviousRating", victimPreviousRating);
        fields.put("victimCurrentRating", victimCurrentRating);
        fields.put("victimDelta", victimDelta);
        fields.put("victimRatingChanged", victimRatingChanged);
        fields.put("victimRatingFrozen", victimRatingFrozen);
        fields.put("formula", formulaSnapshot());
        fields.put("suspicious", !suspiciousReasons.isEmpty());
        if (!suspiciousReasons.isEmpty()) fields.put("suspiciousReasons", suspiciousReasons);

        writeEvent("rating_kill", fields, null);
    }

    public static void logSkippedKill(ServerPlayer killer, ServerPlayer victim, String reason, Map<String, Object> details) {
        if (!Config.AuditLogSkippedKills.get()) return;

        Map<String, Object> fields = baseKillFields(killer, victim);
        fields.put("reason", reason);
        if (details != null && !details.isEmpty()) fields.put("details", details);

        writeEvent("rating_kill_skipped", fields, skippedKillRateLimitKey(killer.getUUID(), victim.getUUID(), reason));
    }

    public static void logManualRatingChange(
            CommandSourceStack source,
            String action,
            ServerPlayer target,
            double previousRating,
            double currentRating,
            Double commandAmount
    ) {
        if (!Config.AuditLogAdminChanges.get()) return;

        double delta = currentRating - previousRating;
        List<String> suspiciousReasons = suspiciousRatingReasons(delta, 0.0, currentRating, previousRating);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("action", action);
        fields.put("actor", actor(source));
        fields.put("target", player(target));
        fields.put("previousRating", previousRating);
        fields.put("currentRating", currentRating);
        fields.put("delta", delta);
        if (commandAmount != null) fields.put("commandAmount", commandAmount);
        fields.put("suspicious", !suspiciousReasons.isEmpty());
        if (!suspiciousReasons.isEmpty()) fields.put("suspiciousReasons", suspiciousReasons);

        writeEvent("admin_rating_change", fields, null);
    }

    public static void logManualRatingChange(
            CommandSourceStack source,
            String action,
            String targetName,
            UUID targetUuid,
            double previousRating,
            double currentRating,
            Double commandAmount
    ) {
        if (!Config.AuditLogAdminChanges.get()) return;

        double delta = currentRating - previousRating;
        List<String> suspiciousReasons = suspiciousRatingReasons(delta, 0.0, currentRating, previousRating);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("action", action);
        fields.put("actor", actor(source));
        fields.put("target", player(targetName, targetUuid));
        fields.put("previousRating", previousRating);
        fields.put("currentRating", currentRating);
        fields.put("delta", delta);
        if (commandAmount != null) fields.put("commandAmount", commandAmount);
        fields.put("suspicious", !suspiciousReasons.isEmpty());
        if (!suspiciousReasons.isEmpty()) fields.put("suspiciousReasons", suspiciousReasons);

        writeEvent("admin_rating_change", fields, null);
    }

    public static void logManualFrozenChange(CommandSourceStack source, ServerPlayer target, boolean previousFrozen, boolean currentFrozen) {
        if (!Config.AuditLogAdminChanges.get()) return;

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("actor", actor(source));
        fields.put("target", player(target));
        fields.put("previousFrozen", previousFrozen);
        fields.put("currentFrozen", currentFrozen);

        writeEvent("admin_freeze_change", fields, null);
    }

    public static void logManualFrozenChange(CommandSourceStack source, String targetName, UUID targetUuid, boolean previousFrozen, boolean currentFrozen) {
        if (!Config.AuditLogAdminChanges.get()) return;

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("actor", actor(source));
        fields.put("target", player(targetName, targetUuid));
        fields.put("previousFrozen", previousFrozen);
        fields.put("currentFrozen", currentFrozen);

        writeEvent("admin_freeze_change", fields, null);
    }

    public static void logConfigChange(CommandSourceStack source, String configKey, Object previousValue, Object currentValue) {
        if (!Config.AuditLogAdminChanges.get()) return;

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("actor", actor(source));
        fields.put("configKey", configKey);
        fields.put("previousValue", previousValue);
        fields.put("currentValue", currentValue);

        List<String> suspiciousReasons = suspiciousConfigReasons(configKey, currentValue);
        fields.put("suspicious", !suspiciousReasons.isEmpty());
        if (!suspiciousReasons.isEmpty()) fields.put("suspiciousReasons", suspiciousReasons);

        writeEvent("admin_config_change", fields, null);
    }

    private static Map<String, Object> baseKillFields(ServerPlayer killer, ServerPlayer victim) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("killer", player(killer));
        fields.put("victim", player(victim));
        if (Config.AuditIncludeCoordinates.get()) {
            fields.put("killerPosition", position(killer));
            fields.put("victimPosition", position(victim));
        }
        return fields;
    }

    private static Map<String, Object> player(ServerPlayer player) {
        return player(player.getGameProfile().getName(), player.getUUID());
    }

    private static Map<String, Object> player(String name, UUID uuid) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", name);
        fields.put("uuid", uuid.toString());
        return fields;
    }

    private static Map<String, Object> actor(CommandSourceStack source) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", source.getTextName());

        Entity entity = source.getEntity();
        if (entity != null) {
            fields.put("entityType", entity.getType().toString());
            fields.put("uuid", entity.getUUID().toString());
        } else {
            fields.put("entityType", "server");
        }

        return fields;
    }

    private static Map<String, Object> position(ServerPlayer player) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("dimension", player.level().dimension().location().toString());
        fields.put("x", player.blockPosition().getX());
        fields.put("y", player.blockPosition().getY());
        fields.put("z", player.blockPosition().getZ());
        return fields;
    }

    private static Map<String, Object> formulaSnapshot() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("gain", Config.Gain.get());
        fields.put("loss", Config.Loss.get());
        fields.put("killerMultiplier", Config.KillerMultiplier.get());
        fields.put("claimMultiplier", Config.ClaimMultiplier.get());
        fields.put("targetMultiplier", Config.TargetMultiplier.get());
        fields.put("completeLoss", Config.LoseCompleteRatingOnDeath.get());
        fields.put("preventNegativeRating", Config.PreventNegativeRating.get());
        fields.put("spawnKillProtectionSeconds", Config.SpawnKillProtectionSeconds.get());
        fields.put("spawnKillProtectionMaxSeconds", Config.SpawnKillProtectionMaxSeconds.get());
        fields.put("spawnKillProtectionMultiplier", Config.SpawnKillProtectionMultiplier.get());
        fields.put("combatPowerEnabled", Config.CombatPowerEnabled.get());
        fields.put("combatPowerMinReadyArmor", Config.CombatPowerMinReadyArmor.get());
        fields.put("combatPowerMinReadyAttackDamage", Config.CombatPowerMinReadyAttackDamage.get());
        fields.put("combatPowerArmorWeight", Config.CombatPowerArmorWeight.get());
        fields.put("combatPowerToughnessWeight", Config.CombatPowerToughnessWeight.get());
        fields.put("combatPowerAttackDamageWeight", Config.CombatPowerAttackDamageWeight.get());
        fields.put("combatPowerMinGainCoefficient", Config.CombatPowerMinGainCoefficient.get());
        fields.put("combatPowerReverseAttemptPenaltyEnabled", Config.CombatPowerReverseAttemptPenaltyEnabled.get());
        return fields;
    }

    private static String killOutcome(boolean killerRatingChanged, boolean victimRatingChanged, boolean killerFrozen, boolean victimFrozen) {
        if (killerRatingChanged && victimRatingChanged) return "applied";
        if (killerRatingChanged || victimRatingChanged) return "partially_applied";
        if (killerFrozen || victimFrozen) return "skipped_frozen";
        return "no_rating_change";
    }

    private static List<String> suspiciousRatingReasons(
            double killerDelta,
            double victimDelta,
            double killerCurrentRating,
            double victimCurrentRating
    ) {
        List<String> reasons = new ArrayList<>();
        double threshold = Config.AuditSuspiciousDeltaThreshold.get();

        if (hasSuspiciousRatingValue(killerDelta)
                || hasSuspiciousRatingValue(victimDelta)
                || hasSuspiciousRatingValue(killerCurrentRating)
                || hasSuspiciousRatingValue(victimCurrentRating)) {
            reasons.add("non_finite_rating_value");
        }

        if (threshold > 0.0 && (Math.abs(killerDelta) >= threshold || Math.abs(victimDelta) >= threshold)) {
            reasons.add("large_rating_delta");
        }

        return reasons;
    }

    private static boolean hasSuspiciousRatingValue(double value) {
        return !Double.isFinite(value);
    }

    private static List<String> suspiciousConfigReasons(String configKey, Object currentValue) {
        List<String> reasons = new ArrayList<>();

        if ("label.pvprating.config.spawn_kill_seconds".equals(configKey)
                && currentValue instanceof Integer seconds
                && seconds == 0) {
            reasons.add("spawn_kill_protection_disabled");
        }

        if ("label.pvprating.config.combat_power.enabled".equals(configKey)
                && currentValue instanceof Boolean enabled
                && !enabled) {
            reasons.add("combat_power_disabled");
        }

        return reasons;
    }

    private static String skippedKillRateLimitKey(UUID killerUuid, UUID victimUuid, String reason) {
        return "rating_kill_skipped:" + reason + ":" + killerUuid + ":" + victimUuid;
    }

    private static synchronized void writeEvent(String eventType, Map<String, Object> fields, String rateLimitKey) {
        if (!Config.AuditLoggingEnabled.get()) return;

        long nowMillis = java.lang.System.currentTimeMillis();
        cleanupRateLimitRecords(nowMillis);

        if (rateLimitKey != null && shouldSuppress(rateLimitKey, eventType, nowMillis)) return;

        writeLine(buildEventLine(eventType, fields));
    }

    private static boolean shouldSuppress(String rateLimitKey, String eventType, long nowMillis) {
        int windowSeconds = Config.AuditRateLimitWindowSeconds.get();
        if (windowSeconds <= 0) return false;

        long windowMillis = windowSeconds * 1000L;
        RateLimitRecord record = rateLimitRecords.get(rateLimitKey);
        if (record == null) {
            rateLimitRecords.put(rateLimitKey, new RateLimitRecord(nowMillis, eventType));
            return false;
        }

        if (nowMillis - record.windowStartMillis >= windowMillis) {
            writeSuppressionSummary(rateLimitKey, record, windowSeconds);
            record.windowStartMillis = nowMillis;
            record.eventType = eventType;
            record.suppressedCount = 0;
            return false;
        }

        record.suppressedCount++;
        return true;
    }

    private static void cleanupRateLimitRecords(long nowMillis) {
        if (rateLimitRecords.isEmpty()) return;

        long windowMillis = Math.max(1, Config.AuditRateLimitWindowSeconds.get()) * 1000L;
        Iterator<Map.Entry<String, RateLimitRecord>> iterator = rateLimitRecords.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, RateLimitRecord> entry = iterator.next();
            RateLimitRecord record = entry.getValue();
            if (nowMillis - record.windowStartMillis >= windowMillis * 2) {
                writeSuppressionSummary(entry.getKey(), record, Config.AuditRateLimitWindowSeconds.get());
                iterator.remove();
            }
        }

        while (rateLimitRecords.size() > RATE_LIMIT_RECORD_LIMIT) {
            Iterator<Map.Entry<String, RateLimitRecord>> limitIterator = rateLimitRecords.entrySet().iterator();
            if (!limitIterator.hasNext()) return;

            Map.Entry<String, RateLimitRecord> entry = limitIterator.next();
            writeSuppressionSummary(entry.getKey(), entry.getValue(), Config.AuditRateLimitWindowSeconds.get());
            limitIterator.remove();
        }
    }

    private static void writeSuppressionSummary(String rateLimitKey, RateLimitRecord record, int windowSeconds) {
        if (record.suppressedCount <= 0) return;

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("suppressedEvent", record.eventType);
        fields.put("rateLimitKey", rateLimitKey);
        fields.put("suppressedCount", record.suppressedCount);
        fields.put("windowSeconds", windowSeconds);

        writeLine(buildEventLine("audit_suppressed", fields));
    }

    private static String buildEventLine(String eventType, Map<String, Object> fields) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", Instant.now().toString());
        event.put("schemaVersion", 1);
        event.put("event", eventType);
        event.putAll(fields);
        return toJson(event) + java.lang.System.lineSeparator();
    }

    private static void writeLine(String line) {
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);

        try {
            Path logFile = auditLogFile();
            Files.createDirectories(logFile.getParent());
            rotateIfNeeded(logFile, bytes.length);
            Files.write(logFile, bytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            PvPRatingMod.LOGGER.warn("Failed to write PvPRating audit log.", exception);
        } catch (RuntimeException exception) {
            PvPRatingMod.LOGGER.warn("Failed to build PvPRating audit log event.", exception);
        }
    }

    private static Path auditLogFile() {
        return FMLPaths.GAMEDIR.get().resolve("logs").resolve(AUDIT_FILE_NAME);
    }

    private static void rotateIfNeeded(Path logFile, int pendingBytes) throws IOException {
        int maxFileSizeBytes = Config.AuditMaxFileSizeBytes.get();
        if (Files.exists(logFile) && Files.size(logFile) + pendingBytes <= maxFileSizeBytes) return;
        if (!Files.exists(logFile)) return;

        int maxFiles = Config.AuditMaxFiles.get();
        if (maxFiles <= 1) {
            Files.deleteIfExists(logFile);
            return;
        }

        int maxBackupIndex = maxFiles - 1;
        Files.deleteIfExists(rotatedLogFile(maxBackupIndex));

        for (int index = maxBackupIndex - 1; index >= 1; index--) {
            Path source = rotatedLogFile(index);
            if (Files.exists(source)) {
                Files.move(source, rotatedLogFile(index + 1), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        Files.move(logFile, rotatedLogFile(1), StandardCopyOption.REPLACE_EXISTING);
    }

    private static Path rotatedLogFile(int index) {
        return FMLPaths.GAMEDIR.get().resolve("logs").resolve("pvprating-audit." + index + ".log");
    }

    private static String toJson(Object value) {
        StringBuilder builder = new StringBuilder();
        appendJsonValue(builder, value);
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendJsonValue(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String stringValue) {
            appendJsonString(builder, stringValue);
        } else if (value instanceof Boolean booleanValue) {
            builder.append(booleanValue);
        } else if (value instanceof Float floatValue) {
            appendFloatingPointValue(builder, floatValue);
        } else if (value instanceof Double doubleValue) {
            appendFloatingPointValue(builder, doubleValue);
        } else if (value instanceof Number numberValue) {
            builder.append(numberValue);
        } else if (value instanceof Map<?, ?> mapValue) {
            appendJsonObject(builder, (Map<Object, Object>) mapValue);
        } else if (value instanceof Iterable<?> iterableValue) {
            appendJsonArray(builder, iterableValue);
        } else {
            appendJsonString(builder, String.valueOf(value));
        }
    }

    private static void appendFloatingPointValue(StringBuilder builder, Number value) {
        double doubleValue = value.doubleValue();
        if (Double.isFinite(doubleValue)) {
            builder.append(value);
        } else {
            appendJsonString(builder, String.valueOf(value));
        }
    }

    private static void appendJsonObject(StringBuilder builder, Map<Object, Object> map) {
        builder.append('{');
        boolean first = true;

        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            if (!first) builder.append(',');
            first = false;

            appendJsonString(builder, String.valueOf(entry.getKey()));
            builder.append(':');
            appendJsonValue(builder, entry.getValue());
        }

        builder.append('}');
    }

    private static void appendJsonArray(StringBuilder builder, Iterable<?> values) {
        builder.append('[');
        boolean first = true;

        for (Object value : values) {
            if (!first) builder.append(',');
            first = false;

            appendJsonValue(builder, value);
        }

        builder.append(']');
    }

    private static void appendJsonString(StringBuilder builder, String value) {
        builder.append('"');

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }

        builder.append('"');
    }

    private static class RateLimitRecord {
        private long windowStartMillis;
        private String eventType;
        private int suppressedCount;

        private RateLimitRecord(long windowStartMillis, String eventType) {
            this.windowStartMillis = windowStartMillis;
            this.eventType = eventType;
        }
    }
}
