package dev.pvprating.configs;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class Config {

    public static ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static ForgeConfigSpec.BooleanValue StartupWarning;

    public static ForgeConfigSpec.BooleanValue System;
    public static ForgeConfigSpec.BooleanValue AllowCommandBlockAdminCommands;

    public static ForgeConfigSpec.BooleanValue EnableDisplay;
    public static ForgeConfigSpec.ConfigValue<Integer> DisplayCooldown;
    public static ForgeConfigSpec.ConfigValue<Integer> Color;

    public static ForgeConfigSpec.BooleanValue Bold;
    public static ForgeConfigSpec.BooleanValue Italic;
    public static ForgeConfigSpec.BooleanValue Underlined;
    public static ForgeConfigSpec.BooleanValue Strikethrough;

    public static ForgeConfigSpec.ConfigValue<String> Display1;
    public static ForgeConfigSpec.ConfigValue<String> Display2;
    public static ForgeConfigSpec.BooleanValue RankAbsoluteSystem;
    public static ForgeConfigSpec.ConfigValue<List<? extends Double>> RankThresholds;

    public static ForgeConfigSpec.BooleanValue LoseCompleteRatingOnDeath;
    public static ForgeConfigSpec.BooleanValue PreventNegativeRating;
    public static ForgeConfigSpec.IntValue SpawnKillProtectionSeconds;
    public static ForgeConfigSpec.IntValue SpawnKillProtectionMaxSeconds;
    public static ForgeConfigSpec.ConfigValue<Double> SpawnKillProtectionMultiplier;
    public static ForgeConfigSpec.BooleanValue CombatPowerEnabled;
    public static ForgeConfigSpec.ConfigValue<Double> CombatPowerMinReadyArmor;
    public static ForgeConfigSpec.ConfigValue<Double> CombatPowerMinReadyAttackDamage;
    public static ForgeConfigSpec.ConfigValue<Double> CombatPowerArmorWeight;
    public static ForgeConfigSpec.ConfigValue<Double> CombatPowerToughnessWeight;
    public static ForgeConfigSpec.ConfigValue<Double> CombatPowerAttackDamageWeight;
    public static ForgeConfigSpec.ConfigValue<Double> CombatPowerMinGainCoefficient;
    public static ForgeConfigSpec.BooleanValue CombatPowerReverseAttemptPenaltyEnabled;
    public static ForgeConfigSpec.BooleanValue TownyDisableRatingSameTown;
    public static ForgeConfigSpec.BooleanValue TownyDisableRatingSameNation;
    public static ForgeConfigSpec.BooleanValue TownyDisableRatingAlliedNations;
    public static ForgeConfigSpec.BooleanValue TownyDisableRatingMutualFriends;

    public static ForgeConfigSpec.BooleanValue AuditLoggingEnabled;
    public static ForgeConfigSpec.BooleanValue AuditLogAppliedKills;
    public static ForgeConfigSpec.BooleanValue AuditLogSkippedKills;
    public static ForgeConfigSpec.BooleanValue AuditLogAdminChanges;
    public static ForgeConfigSpec.BooleanValue AuditIncludeCoordinates;
    public static ForgeConfigSpec.IntValue AuditMaxFileSizeBytes;
    public static ForgeConfigSpec.IntValue AuditMaxFiles;
    public static ForgeConfigSpec.IntValue AuditRateLimitWindowSeconds;
    public static ForgeConfigSpec.DoubleValue AuditSuspiciousDeltaThreshold;
    public static ForgeConfigSpec.BooleanValue RatingAnomalyDetectionEnabled;
    public static ForgeConfigSpec.BooleanValue RatingAnomalyConsoleAlertsEnabled;
    public static ForgeConfigSpec.IntValue RatingAnomalyWindowSeconds;
    public static ForgeConfigSpec.DoubleValue RatingAnomalyMaxGainInWindow;
    public static ForgeConfigSpec.DoubleValue RatingAnomalyMaxLossInWindow;
    public static ForgeConfigSpec.DoubleValue RatingAnomalyMaxSingleDelta;
    public static ForgeConfigSpec.IntValue RatingAnomalyMaxChangesInWindow;
    public static ForgeConfigSpec.IntValue RatingAnomalyMaxPairChangesInWindow;
    public static ForgeConfigSpec.IntValue RatingAnomalyAlertCooldownSeconds;
    public static ForgeConfigSpec.BooleanValue RatingAnomalyTrackAdminChanges;

    public static ForgeConfigSpec.ConfigValue<Double> MinimumValue;
    public static ForgeConfigSpec.ConfigValue<Double> MaximumValue;

    public static ForgeConfigSpec.ConfigValue<Double> Gain;
    public static ForgeConfigSpec.ConfigValue<Double> Loss;

    public static ForgeConfigSpec.ConfigValue<Double> KillerMultiplier;
    public static ForgeConfigSpec.ConfigValue<Double> ClaimMultiplier;
    public static ForgeConfigSpec.ConfigValue<Double> TargetMultiplier;


    static {
        builder.comment("""
                Make sure you do the calculations correctly, you can use the link below to do the math yourself.
                
                Killer is calculated like this: Your Rating + Gain On Killing + (Your Rating * Killer-Multiplier) + (Target Rating * Claim-Multiplier)
                
                Target is calculated like this: Your Rating - Loss On Death - (Your Rating * Target-Multiplier)
                
                If LossCompleteRatingOnDeath is true: Your Rating = -LossOnDeath - (Rating * Target-Multiplier)
                
                For colors, pick an RGB color from the first page and copy the R, G, and B, numbers. Then put these numbers inside the second page to get a Decimal RGB Color
                https://www.rapidtables.com/web/color/RGB_Color.html
                https://www.checkyourmath.com/convert/color/rgb_decimal.php
                
                For calculations, check there! https://onlinegdb.com/BCyy-0Pi-Q
                """);

        StartupWarning = builder.define("Enable the warning in the server start", true);

        System = builder.define("Enable the default maths, disable the display to prevent the name change", true);
        AllowCommandBlockAdminCommands = builder.comment("If enabled, command blocks and other non-player command sources with operator-level permission can run PvPRating admin commands.")
                .define("allowCommandBlockAdminCommands", false);

        EnableDisplay = builder.define("Enable the display", true);
        DisplayCooldown = builder.define("How much ticks before displays get updated", 100);
        Color = builder.define("RGB Decimal color of the rating in chat", 16755200);

        Bold = builder.define("Make the color bold", true);
        Italic = builder.define("Make the color italic", false);
        Underlined = builder.define("Underline the color", false);
        Strikethrough = builder.define("Strikethrough the color", false);

        Display1 = builder.define("Text before the value", " [");
        Display2 = builder.define("Text after the value", "]");
        RankAbsoluteSystem = builder.comment("If enabled, rank and sword icon levels are scaled from the current highest known rating. If disabled, rankThresholds are used.")
                .define("rankAbsoluteSystem", false);
        RankThresholds = builder.comment("Rating thresholds for the 10 rank levels. Rank 1 starts at the first value, rank 10 starts at the last value.")
                .defineList("rankThresholds", defaultRankThresholds(), Config::isValidRankThreshold);

        LoseCompleteRatingOnDeath = builder.define("Loss the equivalent of your entire rating on death", false);
        PreventNegativeRating = builder.comment("If enabled, the player's rating cannot become lower than 0.")
                .define("preventNegativeRating", true);
        SpawnKillProtectionSeconds = builder.comment("Minimum seconds between repeated rating-counted kills for the same killer-victim pair. 0 disables the protection.")
                .defineInRange("spawnKillProtectionSeconds", 60, 0, Integer.MAX_VALUE);
        SpawnKillProtectionMaxSeconds = builder.comment("Maximum escalated spawn-kill protection time for one killer-victim pair.")
                .defineInRange("spawnKillProtectionMaxSeconds", 12 * 60 * 60, 0, Integer.MAX_VALUE);
        SpawnKillProtectionMultiplier = builder.comment("Multiplier applied to the pair cooldown when the same killer kills the same victim before the cooldown expires.")
                .defineInRange("spawnKillProtectionMultiplier", 2.0, 1.0, Double.MAX_VALUE);
        CombatPowerEnabled = builder.comment("If enabled, PvPRating estimates player combat readiness from armor, armor toughness, and attack damage.")
                .define("combatPowerEnabled", true);
        CombatPowerMinReadyArmor = builder.comment("Minimum armor points required for a victim to be considered ready enough to bypass active pair cooldown.")
                .defineInRange("combatPowerMinReadyArmor", 8.0, 0.0, Double.MAX_VALUE);
        CombatPowerMinReadyAttackDamage = builder.comment("Minimum attack damage required for a victim to be considered ready enough to bypass active pair cooldown.")
                .defineInRange("combatPowerMinReadyAttackDamage", 4.0, 0.0, Double.MAX_VALUE);
        CombatPowerArmorWeight = builder.comment("Weight of armor points in the combat power calculation.")
                .defineInRange("combatPowerArmorWeight", 1.0, 0.0, Double.MAX_VALUE);
        CombatPowerToughnessWeight = builder.comment("Weight of armor toughness in the combat power calculation.")
                .defineInRange("combatPowerToughnessWeight", 1.0, 0.0, Double.MAX_VALUE);
        CombatPowerAttackDamageWeight = builder.comment("Weight of attack damage in the combat power calculation.")
                .defineInRange("combatPowerAttackDamageWeight", 2.0, 0.0, Double.MAX_VALUE);
        CombatPowerMinGainCoefficient = builder.comment("Minimum rating change coefficient when the victim has lower combat power than the killer.")
                .defineInRange("combatPowerMinGainCoefficient", 0.15, 0.0, 1.0);
        CombatPowerReverseAttemptPenaltyEnabled = builder.comment("If enabled, a comeback kill is penalized when the opposite pair has repeated blocked kills.")
                .define("combatPowerReverseAttemptPenaltyEnabled", true);
        TownyDisableRatingSameTown = builder.comment("If Towny is installed, skip default PvP rating changes when killer and victim are residents of the same town.")
                .define("townyDisableRatingSameTown", true);
        TownyDisableRatingSameNation = builder.comment("If Towny is installed, skip default PvP rating changes when killer and victim are in the same nation.")
                .define("townyDisableRatingSameNation", true);
        TownyDisableRatingAlliedNations = builder.comment("If Towny is installed, skip default PvP rating changes when killer and victim are in mutually allied nations.")
                .define("townyDisableRatingAlliedNations", true);
        TownyDisableRatingMutualFriends = builder.comment("If Towny is installed, skip default PvP rating changes when killer and victim have added each other as Towny friends.")
                .define("townyDisableRatingMutualFriends", true);
        AuditLoggingEnabled = builder.comment("Writes PvPRating security/audit events to logs/pvprating-audit.log.")
                .define("auditLoggingEnabled", true);
        AuditLogAppliedKills = builder.comment("Logs valid PvP kills that apply, partially apply, or try to apply rating changes.")
                .define("auditLogAppliedKills", true);
        AuditLogSkippedKills = builder.comment("Logs PvP rating changes skipped by protections such as Towny or spawn-kill protection.")
                .define("auditLogSkippedKills", true);
        AuditLogAdminChanges = builder.comment("Logs operator rating, freeze, and PvPRating config changes.")
                .define("auditLogAdminChanges", true);
        AuditIncludeCoordinates = builder.comment("If enabled, audit events include block coordinates and dimension for involved players.")
                .define("auditIncludeCoordinates", false);
        AuditMaxFileSizeBytes = builder.comment("Maximum size of logs/pvprating-audit.log before rotation.")
                .defineInRange("auditMaxFileSizeBytes", 10 * 1024 * 1024, 64 * 1024, Integer.MAX_VALUE);
        AuditMaxFiles = builder.comment("Maximum audit log files to keep, including the current file.")
                .defineInRange("auditMaxFiles", 5, 1, 100);
        AuditRateLimitWindowSeconds = builder.comment("Suppresses repeated skipped-kill audit entries for the same killer, victim, and reason during this window. 0 disables suppression.")
                .defineInRange("auditRateLimitWindowSeconds", 60, 0, 24 * 60 * 60);
        AuditSuspiciousDeltaThreshold = builder.comment("A single kill rating delta at or above this absolute value is marked suspicious. 0 disables this flag.")
                .defineInRange("auditSuspiciousDeltaThreshold", 1000.0, 0.0, Double.MAX_VALUE);
        RatingAnomalyDetectionEnabled = builder.comment("If enabled, PvPRating checks rating changes for suspicious patterns.")
                .define("ratingAnomalyDetectionEnabled", true);
        RatingAnomalyConsoleAlertsEnabled = builder.comment("If enabled, detected rating anomalies are written to the server console.")
                .define("ratingAnomalyConsoleAlertsEnabled", true);
        RatingAnomalyWindowSeconds = builder.comment("Time window used for rating anomaly aggregation.")
                .defineInRange("ratingAnomalyWindowSeconds", 300, 1, 24 * 60 * 60);
        RatingAnomalyMaxGainInWindow = builder.comment("Maximum positive rating gained by one player during the anomaly window before an alert is emitted. 0 disables this check.")
                .defineInRange("ratingAnomalyMaxGainInWindow", 250.0, 0.0, Double.MAX_VALUE);
        RatingAnomalyMaxLossInWindow = builder.comment("Maximum rating lost by one player during the anomaly window before an alert is emitted. 0 disables this check.")
                .defineInRange("ratingAnomalyMaxLossInWindow", 250.0, 0.0, Double.MAX_VALUE);
        RatingAnomalyMaxSingleDelta = builder.comment("Maximum absolute rating change in one event before an alert is emitted. 0 disables this check.")
                .defineInRange("ratingAnomalyMaxSingleDelta", 1000.0, 0.0, Double.MAX_VALUE);
        RatingAnomalyMaxChangesInWindow = builder.comment("Maximum rating-change events for one player during the anomaly window before an alert is emitted. 0 disables this check.")
                .defineInRange("ratingAnomalyMaxChangesInWindow", 20, 0, Integer.MAX_VALUE);
        RatingAnomalyMaxPairChangesInWindow = builder.comment("Maximum rating-counted kills for one killer-victim pair during the anomaly window before an alert is emitted. 0 disables this check.")
                .defineInRange("ratingAnomalyMaxPairChangesInWindow", 5, 0, Integer.MAX_VALUE);
        RatingAnomalyAlertCooldownSeconds = builder.comment("Minimum seconds between repeated console alerts for the same anomaly key. 0 disables suppression.")
                .defineInRange("ratingAnomalyAlertCooldownSeconds", 60, 0, 24 * 60 * 60);
        RatingAnomalyTrackAdminChanges = builder.comment("If enabled, manual operator rating changes are included in anomaly detection.")
                .define("ratingAnomalyTrackAdminChanges", true);
        MinimumValue = builder.defineInRange("Rating Minimum Value", -Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE);
        MaximumValue = builder.defineInRange("Rating Maximum Value", Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE);

        Gain = builder.defineInRange("Rating Gain On Killing", 1.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        Loss = builder.defineInRange("Rating Loss On Death", 0.0, -Double.MAX_VALUE, Double.MAX_VALUE);

        KillerMultiplier = builder.defineInRange("Killer-Multiplier multiply from your own rating every time you kill someone 1 = 100%", 0.0, -Double.MAX_VALUE, Double.MAX_VALUE);
        ClaimMultiplier = builder.defineInRange("Claim-Multiplier, how much rating you take from your victim 1 = 100%", 0.09, -Double.MAX_VALUE, Double.MAX_VALUE);
        TargetMultiplier = builder.defineInRange("Target-Multiplier, how much rating is subtracted when you get killed 1 = 100%", 0.1, -Double.MAX_VALUE, Double.MAX_VALUE);

        SPEC = builder.build();
    }

    private static List<Double> defaultRankThresholds() {
        return List.of(
                0.0,
                200.0 / 9.0,
                400.0 / 9.0,
                200.0 / 3.0,
                800.0 / 9.0,
                1000.0 / 9.0,
                400.0 / 3.0,
                1400.0 / 9.0,
                1600.0 / 9.0,
                200.0
        );
    }

    private static boolean isValidRankThreshold(Object value) {
        if (!(value instanceof Number number)) return false;
        double threshold = number.doubleValue();
        return Double.isFinite(threshold) && threshold >= 0.0;
    }
}
