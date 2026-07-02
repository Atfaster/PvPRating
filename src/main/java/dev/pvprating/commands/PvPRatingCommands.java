package dev.pvprating.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.pvprating.PvPRatingMod;
import dev.pvprating.configs.Config;
import dev.pvprating.events.DisplayEvents;
import dev.pvprating.utils.OfflineRatingData;
import dev.pvprating.utils.RatingAnomalyDetector;
import dev.pvprating.utils.RatingAuditLogger;
import dev.pvprating.utils.RatingData;
import dev.pvprating.utils.RatingLeaderboardData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static dev.pvprating.utils.mods.PvPRatingUtils.MAX_RATING_LEVELS;

@EventBusSubscriber
public class PvPRatingCommands {
    private static final int ADMIN_PERMISSION_LEVEL = 4;
    private static final int TOP_PAGE_SIZE = 10;
    private static final int MIN_DISPLAY_COOLDOWN_TICKS = 1;
    private static final int MIN_RGB_DECIMAL = 0;
    private static final int MAX_RGB_DECIMAL = 0xFFFFFF;

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pvprating")
                .executes(context -> sendModDescription(context.getSource()))
                .then(Commands.literal("top")
                        .executes(context -> sendTop(context.getSource(), false, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> sendTop(
                                        context.getSource(),
                                        false,
                                        IntegerArgumentType.getInteger(context, "page")
                                )))
                        .then(Commands.literal("online")
                                .executes(context -> sendTop(context.getSource(), true, 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> sendTop(
                                                context.getSource(),
                                                true,
                                                IntegerArgumentType.getInteger(context, "page")
                                        )))))
                .then(Commands.literal("get")
                        .executes(context -> sendUsage(context.getSource(), "message.pvprating.command.usage.get_player"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PvPRatingCommands::suggestKnownPlayers)
                                .executes(context -> sendPlayerRating(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player")
                                ))))
                .then(Commands.literal("set")
                        .requires(PvPRatingCommands::isAdmin)
                        .executes(context -> sendUsage(context.getSource(), "message.pvprating.command.usage.set_player"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PvPRatingCommands::suggestKnownPlayers)
                                .executes(context -> sendUsage(context.getSource(), "message.pvprating.command.usage.set_rating"))
                                .then(Commands.argument("rating", StringArgumentType.word())
                                        .executes(context -> setRatingFromInput(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "rating")
                                        )))))
                .then(Commands.literal("add")
                        .requires(PvPRatingCommands::isAdmin)
                        .executes(context -> sendUsage(context.getSource(), "message.pvprating.command.usage.add_player"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PvPRatingCommands::suggestKnownPlayers)
                                .executes(context -> sendUsage(context.getSource(), "message.pvprating.command.usage.add_amount"))
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(context -> addRatingFromInput(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "amount")
                                        )))))
                .then(Commands.literal("increase")
                        .requires(PvPRatingCommands::isAdmin)
                        .executes(context -> sendUsage(context.getSource(), "message.pvprating.command.usage.increase_player"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PvPRatingCommands::suggestKnownPlayers)
                                .executes(context -> sendUsage(context.getSource(), "message.pvprating.command.usage.increase_amount"))
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(context -> addRatingFromInput(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "amount")
                                        )))))
                .then(Commands.literal("remove")
                        .requires(PvPRatingCommands::isAdmin)
                        .executes(context -> sendUsage(context.getSource(), "message.pvprating.command.usage.remove_player"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PvPRatingCommands::suggestKnownPlayers)
                                .executes(context -> sendUsage(context.getSource(), "message.pvprating.command.usage.remove_amount"))
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(context -> subtractRatingFromInput(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "amount")
                                        )))))
                .then(Commands.literal("subtract")
                        .requires(PvPRatingCommands::isAdmin)
                        .executes(context -> sendUsage(context.getSource(), "message.pvprating.command.usage.subtract_player"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PvPRatingCommands::suggestKnownPlayers)
                                .executes(context -> sendUsage(context.getSource(), "message.pvprating.command.usage.subtract_amount"))
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(context -> subtractRatingFromInput(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "amount")
                                        )))))
                .then(Commands.literal("freeze")
                        .requires(PvPRatingCommands::isAdmin)
                        .executes(context -> sendUsage(context.getSource(), "message.pvprating.command.usage.freeze_player"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PvPRatingCommands::suggestKnownPlayers)
                                .executes(context -> setFrozen(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        true
                                ))))
                .then(Commands.literal("unfreeze")
                        .requires(PvPRatingCommands::isAdmin)
                        .executes(context -> sendUsage(context.getSource(), "message.pvprating.command.usage.unfreeze_player"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PvPRatingCommands::suggestKnownPlayers)
                                .executes(context -> setFrozen(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        false
                                ))))
                .then(Commands.literal("spawnkill")
                        .requires(PvPRatingCommands::isAdmin)
                        .executes(context -> sendSpawnKillStatus(context.getSource()))
                        .then(Commands.literal("base")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.SpawnKillProtectionSeconds,
                                        "label.pvprating.config.spawn_kill_seconds"
                                ))
                                .then(Commands.argument("seconds", StringArgumentType.word())
                                        .executes(context -> setIntegerConfig(
                                                context.getSource(),
                                                Config.SpawnKillProtectionSeconds,
                                                StringArgumentType.getString(context, "seconds"),
                                                "label.pvprating.config.spawn_kill_seconds",
                                                0,
                                                Integer.MAX_VALUE,
                                                null
                                        ))))
                        .then(Commands.literal("max-seconds")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.SpawnKillProtectionMaxSeconds,
                                        "label.pvprating.config.spawn_kill_max_seconds"
                                ))
                                .then(Commands.argument("seconds", StringArgumentType.word())
                                        .executes(context -> setIntegerConfig(
                                                context.getSource(),
                                                Config.SpawnKillProtectionMaxSeconds,
                                                StringArgumentType.getString(context, "seconds"),
                                                "label.pvprating.config.spawn_kill_max_seconds",
                                                0,
                                                Integer.MAX_VALUE,
                                                null
                                        ))))
                        .then(Commands.literal("multiplier")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.SpawnKillProtectionMultiplier,
                                        "label.pvprating.config.spawn_kill_multiplier"
                                ))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> setDoubleConfigRange(
                                                context.getSource(),
                                                Config.SpawnKillProtectionMultiplier,
                                                StringArgumentType.getString(context, "value"),
                                                "label.pvprating.config.spawn_kill_multiplier",
                                                1.0,
                                                Double.MAX_VALUE,
                                                null
                                        )))))
                .then(Commands.literal("combatpower")
                        .requires(PvPRatingCommands::isAdmin)
                        .executes(context -> sendCombatPowerStatus(context.getSource()))
                        .then(Commands.literal("enabled")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.CombatPowerEnabled,
                                        "label.pvprating.config.combat_power.enabled"
                                ))
                                .then(Commands.argument("enabled", StringArgumentType.word())
                                        .suggests(PvPRatingCommands::suggestBoolean)
                                        .executes(context -> setBooleanConfig(
                                                context.getSource(),
                                                Config.CombatPowerEnabled,
                                                StringArgumentType.getString(context, "enabled"),
                                                "label.pvprating.config.combat_power.enabled",
                                                null
                                        ))))
                        .then(Commands.literal("min-ready-armor")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.CombatPowerMinReadyArmor,
                                        "label.pvprating.config.combat_power.min_ready_armor"
                                ))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> setDoubleConfigRange(
                                                context.getSource(),
                                                Config.CombatPowerMinReadyArmor,
                                                StringArgumentType.getString(context, "value"),
                                                "label.pvprating.config.combat_power.min_ready_armor",
                                                0.0,
                                                Double.MAX_VALUE,
                                                null
                                        ))))
                        .then(Commands.literal("min-ready-attack")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.CombatPowerMinReadyAttackDamage,
                                        "label.pvprating.config.combat_power.min_ready_attack"
                                ))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> setDoubleConfigRange(
                                                context.getSource(),
                                                Config.CombatPowerMinReadyAttackDamage,
                                                StringArgumentType.getString(context, "value"),
                                                "label.pvprating.config.combat_power.min_ready_attack",
                                                0.0,
                                                Double.MAX_VALUE,
                                                null
                                        ))))
                        .then(Commands.literal("armor-weight")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.CombatPowerArmorWeight,
                                        "label.pvprating.config.combat_power.armor_weight"
                                ))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> setDoubleConfigRange(
                                                context.getSource(),
                                                Config.CombatPowerArmorWeight,
                                                StringArgumentType.getString(context, "value"),
                                                "label.pvprating.config.combat_power.armor_weight",
                                                0.0,
                                                Double.MAX_VALUE,
                                                null
                                        ))))
                        .then(Commands.literal("toughness-weight")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.CombatPowerToughnessWeight,
                                        "label.pvprating.config.combat_power.toughness_weight"
                                ))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> setDoubleConfigRange(
                                                context.getSource(),
                                                Config.CombatPowerToughnessWeight,
                                                StringArgumentType.getString(context, "value"),
                                                "label.pvprating.config.combat_power.toughness_weight",
                                                0.0,
                                                Double.MAX_VALUE,
                                                null
                                        ))))
                        .then(Commands.literal("attack-weight")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.CombatPowerAttackDamageWeight,
                                        "label.pvprating.config.combat_power.attack_weight"
                                ))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> setDoubleConfigRange(
                                                context.getSource(),
                                                Config.CombatPowerAttackDamageWeight,
                                                StringArgumentType.getString(context, "value"),
                                                "label.pvprating.config.combat_power.attack_weight",
                                                0.0,
                                                Double.MAX_VALUE,
                                                null
                                        ))))
                        .then(Commands.literal("min-gain-coefficient")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.CombatPowerMinGainCoefficient,
                                        "label.pvprating.config.combat_power.min_gain_coefficient"
                                ))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> setDoubleConfigRange(
                                                context.getSource(),
                                                Config.CombatPowerMinGainCoefficient,
                                                StringArgumentType.getString(context, "value"),
                                                "label.pvprating.config.combat_power.min_gain_coefficient",
                                                0.0,
                                                1.0,
                                                null
                                        ))))
                        .then(Commands.literal("reverse-attempt-penalty")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.CombatPowerReverseAttemptPenaltyEnabled,
                                        "label.pvprating.config.combat_power.reverse_attempt_penalty"
                                ))
                                .then(Commands.argument("enabled", StringArgumentType.word())
                                        .suggests(PvPRatingCommands::suggestBoolean)
                                        .executes(context -> setBooleanConfig(
                                                context.getSource(),
                                                Config.CombatPowerReverseAttemptPenaltyEnabled,
                                                StringArgumentType.getString(context, "enabled"),
                                                "label.pvprating.config.combat_power.reverse_attempt_penalty",
                                                null
                                        )))))
                .then(Commands.literal("system")
                        .requires(PvPRatingCommands::isAdmin)
                        .executes(context -> sendSystemStatus(context.getSource()))
                        .then(Commands.argument("enabled", StringArgumentType.word())
                                .suggests(PvPRatingCommands::suggestBoolean)
                                .executes(context -> setBooleanConfig(
                                        context.getSource(),
                                        Config.System,
                                        StringArgumentType.getString(context, "enabled"),
                                        "label.pvprating.config.system",
                                        null
                                ))))
                .then(Commands.literal("rank")
                        .requires(PvPRatingCommands::isAdmin)
                        .executes(context -> sendRankStatus(context.getSource()))
                        .then(Commands.literal("absolute")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.RankAbsoluteSystem,
                                        "label.pvprating.config.rank.absolute"
                                ))
                                .then(Commands.argument("enabled", StringArgumentType.word())
                                        .suggests(PvPRatingCommands::suggestBoolean)
                                        .executes(context -> setBooleanConfig(
                                                context.getSource(),
                                                Config.RankAbsoluteSystem,
                                                StringArgumentType.getString(context, "enabled"),
                                                "label.pvprating.config.rank.absolute",
                                                () -> syncAllDisplays(context.getSource())
                                        ))))
                        .then(Commands.literal("threshold")
                                .executes(context -> sendRankThresholds(context.getSource()))
                                .then(Commands.argument("level", StringArgumentType.word())
                                        .executes(context -> sendUsage(context.getSource(), "message.pvprating.command.usage.rank_threshold_rating"))
                                        .then(Commands.argument("rating", StringArgumentType.word())
                                                .executes(context -> setRankThresholdFromInput(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "level"),
                                                        StringArgumentType.getString(context, "rating")
                                                ))))))
                .then(Commands.literal("display")
                        .requires(PvPRatingCommands::isAdmin)
                        .executes(context -> sendDisplayStatus(context.getSource()))
                        .then(Commands.argument("enabled", StringArgumentType.word())
                                .suggests(PvPRatingCommands::suggestBoolean)
                                .executes(context -> setBooleanConfig(
                                        context.getSource(),
                                        Config.EnableDisplay,
                                        StringArgumentType.getString(context, "enabled"),
                                        "label.pvprating.config.display.enabled",
                                        () -> syncAllDisplays(context.getSource())
                                )))
                        .then(Commands.literal("cooldown")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.DisplayCooldown,
                                        "label.pvprating.config.display.cooldown"
                                ))
                                .then(Commands.argument("ticks", StringArgumentType.word())
                                        .executes(context -> setIntegerConfig(
                                                context.getSource(),
                                                Config.DisplayCooldown,
                                                StringArgumentType.getString(context, "ticks"),
                                                "label.pvprating.config.display.cooldown",
                                                MIN_DISPLAY_COOLDOWN_TICKS,
                                                Integer.MAX_VALUE,
                                                null
                                        ))))
                        .then(Commands.literal("color")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.Color,
                                        "label.pvprating.config.display.color"
                                ))
                                .then(Commands.argument("rgbDecimal", StringArgumentType.word())
                                        .executes(context -> setIntegerConfig(
                                                context.getSource(),
                                                Config.Color,
                                                StringArgumentType.getString(context, "rgbDecimal"),
                                                "label.pvprating.config.display.color",
                                                MIN_RGB_DECIMAL,
                                                MAX_RGB_DECIMAL,
                                                () -> syncAllDisplays(context.getSource())
                                        ))))
                        .then(Commands.literal("style")
                                .executes(context -> sendDisplayStyleStatus(context.getSource()))
                                .then(Commands.literal("bold")
                                        .executes(context -> sendUsage(context.getSource(), "message.pvprating.command.usage.display_style_bold"))
                                        .then(Commands.argument("enabled", StringArgumentType.word())
                                                .suggests(PvPRatingCommands::suggestBoolean)
                                                .executes(context -> setBooleanConfig(
                                                        context.getSource(),
                                                        Config.Bold,
                                                        StringArgumentType.getString(context, "enabled"),
                                                        "label.pvprating.config.display.bold",
                                                        () -> syncAllDisplays(context.getSource())
                                                ))))
                                .then(Commands.literal("italic")
                                        .executes(context -> sendUsage(context.getSource(), "message.pvprating.command.usage.display_style_italic"))
                                        .then(Commands.argument("enabled", StringArgumentType.word())
                                                .suggests(PvPRatingCommands::suggestBoolean)
                                                .executes(context -> setBooleanConfig(
                                                        context.getSource(),
                                                        Config.Italic,
                                                        StringArgumentType.getString(context, "enabled"),
                                                        "label.pvprating.config.display.italic",
                                                        () -> syncAllDisplays(context.getSource())
                                                ))))
                                .then(Commands.literal("underlined")
                                        .executes(context -> sendUsage(context.getSource(), "message.pvprating.command.usage.display_style_underlined"))
                                        .then(Commands.argument("enabled", StringArgumentType.word())
                                                .suggests(PvPRatingCommands::suggestBoolean)
                                                .executes(context -> setBooleanConfig(
                                                        context.getSource(),
                                                        Config.Underlined,
                                                        StringArgumentType.getString(context, "enabled"),
                                                        "label.pvprating.config.display.underlined",
                                                        () -> syncAllDisplays(context.getSource())
                                                ))))
                                .then(Commands.literal("strikethrough")
                                        .executes(context -> sendUsage(context.getSource(), "message.pvprating.command.usage.display_style_strikethrough"))
                                        .then(Commands.argument("enabled", StringArgumentType.word())
                                                .suggests(PvPRatingCommands::suggestBoolean)
                                                .executes(context -> setBooleanConfig(
                                                        context.getSource(),
                                                        Config.Strikethrough,
                                                        StringArgumentType.getString(context, "enabled"),
                                                        "label.pvprating.config.display.strikethrough",
                                                        () -> syncAllDisplays(context.getSource())
                                                ))))))
                .then(Commands.literal("formula")
                        .requires(PvPRatingCommands::isAdmin)
                        .executes(context -> sendFormulaStatus(context.getSource()))
                        .then(Commands.literal("gain")
                                .executes(context -> sendFormulaConfigDetails(
                                        context.getSource(),
                                        Config.Gain,
                                        "label.pvprating.config.formula.gain",
                                        "description.pvprating.config.formula.gain"
                                ))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> setDoubleConfig(
                                                context.getSource(),
                                                Config.Gain,
                                                StringArgumentType.getString(context, "value"),
                                                "label.pvprating.config.formula.gain",
                                                null
                                        ))))
                        .then(Commands.literal("loss")
                                .executes(context -> sendFormulaConfigDetails(
                                        context.getSource(),
                                        Config.Loss,
                                        "label.pvprating.config.formula.loss",
                                        "description.pvprating.config.formula.loss"
                                ))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> setDoubleConfig(
                                                context.getSource(),
                                                Config.Loss,
                                                StringArgumentType.getString(context, "value"),
                                                "label.pvprating.config.formula.loss",
                                                null
                                        ))))
                        .then(Commands.literal("killer-multiplier")
                                .executes(context -> sendFormulaConfigDetails(
                                        context.getSource(),
                                        Config.KillerMultiplier,
                                        "label.pvprating.config.formula.killer_multiplier",
                                        "description.pvprating.config.formula.killer_multiplier"
                                ))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> setDoubleConfig(
                                                context.getSource(),
                                                Config.KillerMultiplier,
                                                StringArgumentType.getString(context, "value"),
                                                "label.pvprating.config.formula.killer_multiplier",
                                                null
                                        ))))
                        .then(Commands.literal("claim-multiplier")
                                .executes(context -> sendFormulaConfigDetails(
                                        context.getSource(),
                                        Config.ClaimMultiplier,
                                        "label.pvprating.config.formula.claim_multiplier",
                                        "description.pvprating.config.formula.claim_multiplier"
                                ))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> setDoubleConfig(
                                                context.getSource(),
                                                Config.ClaimMultiplier,
                                                StringArgumentType.getString(context, "value"),
                                                "label.pvprating.config.formula.claim_multiplier",
                                                null
                                        ))))
                        .then(Commands.literal("target-multiplier")
                                .executes(context -> sendFormulaConfigDetails(
                                        context.getSource(),
                                        Config.TargetMultiplier,
                                        "label.pvprating.config.formula.target_multiplier",
                                        "description.pvprating.config.formula.target_multiplier"
                                ))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> setDoubleConfig(
                                                context.getSource(),
                                                Config.TargetMultiplier,
                                                StringArgumentType.getString(context, "value"),
                                                "label.pvprating.config.formula.target_multiplier",
                                                null
                                        ))))
                        .then(Commands.literal("complete-loss")
                                .executes(context -> sendFormulaConfigDetails(
                                        context.getSource(),
                                        Config.LoseCompleteRatingOnDeath,
                                        "label.pvprating.config.formula.complete_loss",
                                        "description.pvprating.config.formula.complete_loss"
                                ))
                                .then(Commands.argument("enabled", StringArgumentType.word())
                                        .suggests(PvPRatingCommands::suggestBoolean)
                                        .executes(context -> setBooleanConfig(
                                                context.getSource(),
                                                Config.LoseCompleteRatingOnDeath,
                                                StringArgumentType.getString(context, "enabled"),
                                                "label.pvprating.config.formula.complete_loss",
                                                null
                                        ))))
                        .then(Commands.literal("prevent-negative")
                                .executes(context -> sendFormulaConfigDetails(
                                        context.getSource(),
                                        Config.PreventNegativeRating,
                                        "label.pvprating.config.formula.prevent_negative",
                                        "description.pvprating.config.formula.prevent_negative"
                                ))
                                .then(Commands.argument("enabled", StringArgumentType.word())
                                        .suggests(PvPRatingCommands::suggestBoolean)
                                        .executes(context -> setBooleanConfig(
                                                context.getSource(),
                                                Config.PreventNegativeRating,
                                                StringArgumentType.getString(context, "enabled"),
                                                "label.pvprating.config.formula.prevent_negative",
                                                null
                                        )))))
                .then(Commands.literal("anomaly")
                        .requires(PvPRatingCommands::isAdmin)
                        .executes(context -> sendAnomalyStatus(context.getSource()))
                        .then(Commands.literal("enabled")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.RatingAnomalyDetectionEnabled,
                                        "label.pvprating.config.anomaly.enabled"
                                ))
                                .then(Commands.argument("enabled", StringArgumentType.word())
                                        .suggests(PvPRatingCommands::suggestBoolean)
                                        .executes(context -> setBooleanConfig(
                                                context.getSource(),
                                                Config.RatingAnomalyDetectionEnabled,
                                                StringArgumentType.getString(context, "enabled"),
                                                "label.pvprating.config.anomaly.enabled",
                                                null
                                        ))))
                        .then(Commands.literal("console-alerts")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.RatingAnomalyConsoleAlertsEnabled,
                                        "label.pvprating.config.anomaly.console_alerts"
                                ))
                                .then(Commands.argument("enabled", StringArgumentType.word())
                                        .suggests(PvPRatingCommands::suggestBoolean)
                                        .executes(context -> setBooleanConfig(
                                                context.getSource(),
                                                Config.RatingAnomalyConsoleAlertsEnabled,
                                                StringArgumentType.getString(context, "enabled"),
                                                "label.pvprating.config.anomaly.console_alerts",
                                                null
                                        ))))
                        .then(Commands.literal("window")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.RatingAnomalyWindowSeconds,
                                        "label.pvprating.config.anomaly.window_seconds"
                                ))
                                .then(Commands.argument("seconds", StringArgumentType.word())
                                        .executes(context -> setIntegerConfig(
                                                context.getSource(),
                                                Config.RatingAnomalyWindowSeconds,
                                                StringArgumentType.getString(context, "seconds"),
                                                "label.pvprating.config.anomaly.window_seconds",
                                                1,
                                                24 * 60 * 60,
                                                null
                                        ))))
                        .then(Commands.literal("max-gain")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.RatingAnomalyMaxGainInWindow,
                                        "label.pvprating.config.anomaly.max_gain"
                                ))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> setDoubleConfigRange(
                                                context.getSource(),
                                                Config.RatingAnomalyMaxGainInWindow,
                                                StringArgumentType.getString(context, "value"),
                                                "label.pvprating.config.anomaly.max_gain",
                                                0.0,
                                                Double.MAX_VALUE,
                                                null
                                        ))))
                        .then(Commands.literal("max-loss")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.RatingAnomalyMaxLossInWindow,
                                        "label.pvprating.config.anomaly.max_loss"
                                ))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> setDoubleConfigRange(
                                                context.getSource(),
                                                Config.RatingAnomalyMaxLossInWindow,
                                                StringArgumentType.getString(context, "value"),
                                                "label.pvprating.config.anomaly.max_loss",
                                                0.0,
                                                Double.MAX_VALUE,
                                                null
                                        ))))
                        .then(Commands.literal("single-delta")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.RatingAnomalyMaxSingleDelta,
                                        "label.pvprating.config.anomaly.single_delta"
                                ))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> setDoubleConfigRange(
                                                context.getSource(),
                                                Config.RatingAnomalyMaxSingleDelta,
                                                StringArgumentType.getString(context, "value"),
                                                "label.pvprating.config.anomaly.single_delta",
                                                0.0,
                                                Double.MAX_VALUE,
                                                null
                                        ))))
                        .then(Commands.literal("max-changes")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.RatingAnomalyMaxChangesInWindow,
                                        "label.pvprating.config.anomaly.max_changes"
                                ))
                                .then(Commands.argument("count", StringArgumentType.word())
                                        .executes(context -> setIntegerConfig(
                                                context.getSource(),
                                                Config.RatingAnomalyMaxChangesInWindow,
                                                StringArgumentType.getString(context, "count"),
                                                "label.pvprating.config.anomaly.max_changes",
                                                0,
                                                Integer.MAX_VALUE,
                                                null
                                        ))))
                        .then(Commands.literal("max-pair-changes")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.RatingAnomalyMaxPairChangesInWindow,
                                        "label.pvprating.config.anomaly.max_pair_changes"
                                ))
                                .then(Commands.argument("count", StringArgumentType.word())
                                        .executes(context -> setIntegerConfig(
                                                context.getSource(),
                                                Config.RatingAnomalyMaxPairChangesInWindow,
                                                StringArgumentType.getString(context, "count"),
                                                "label.pvprating.config.anomaly.max_pair_changes",
                                                0,
                                                Integer.MAX_VALUE,
                                                null
                                        ))))
                        .then(Commands.literal("alert-cooldown")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.RatingAnomalyAlertCooldownSeconds,
                                        "label.pvprating.config.anomaly.alert_cooldown"
                                ))
                                .then(Commands.argument("seconds", StringArgumentType.word())
                                        .executes(context -> setIntegerConfig(
                                                context.getSource(),
                                                Config.RatingAnomalyAlertCooldownSeconds,
                                                StringArgumentType.getString(context, "seconds"),
                                                "label.pvprating.config.anomaly.alert_cooldown",
                                                0,
                                                24 * 60 * 60,
                                                null
                                        ))))
                        .then(Commands.literal("track-admin")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.RatingAnomalyTrackAdminChanges,
                                        "label.pvprating.config.anomaly.track_admin"
                                ))
                                .then(Commands.argument("enabled", StringArgumentType.word())
                                        .suggests(PvPRatingCommands::suggestBoolean)
                                        .executes(context -> setBooleanConfig(
                                                context.getSource(),
                                                Config.RatingAnomalyTrackAdminChanges,
                                                StringArgumentType.getString(context, "enabled"),
                                                "label.pvprating.config.anomaly.track_admin",
                                                null
                                        )))))
                .then(Commands.literal("towny")
                        .requires(PvPRatingCommands::isAdmin)
                        .executes(context -> sendTownyStatus(context.getSource()))
                        .then(Commands.literal("same-town")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.TownyDisableRatingSameTown,
                                        "label.pvprating.config.towny.same_town"
                                ))
                                .then(Commands.argument("enabled", StringArgumentType.word())
                                        .suggests(PvPRatingCommands::suggestBoolean)
                                        .executes(context -> setBooleanConfig(
                                                context.getSource(),
                                                Config.TownyDisableRatingSameTown,
                                                StringArgumentType.getString(context, "enabled"),
                                                "label.pvprating.config.towny.same_town",
                                                null
                                        ))))
                        .then(Commands.literal("same-nation")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.TownyDisableRatingSameNation,
                                        "label.pvprating.config.towny.same_nation"
                                ))
                                .then(Commands.argument("enabled", StringArgumentType.word())
                                        .suggests(PvPRatingCommands::suggestBoolean)
                                        .executes(context -> setBooleanConfig(
                                                context.getSource(),
                                                Config.TownyDisableRatingSameNation,
                                                StringArgumentType.getString(context, "enabled"),
                                                "label.pvprating.config.towny.same_nation",
                                                null
                                        ))))
                        .then(Commands.literal("allied-nations")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.TownyDisableRatingAlliedNations,
                                        "label.pvprating.config.towny.allied_nations"
                                ))
                                .then(Commands.argument("enabled", StringArgumentType.word())
                                        .suggests(PvPRatingCommands::suggestBoolean)
                                        .executes(context -> setBooleanConfig(
                                                context.getSource(),
                                                Config.TownyDisableRatingAlliedNations,
                                                StringArgumentType.getString(context, "enabled"),
                                                "label.pvprating.config.towny.allied_nations",
                                                null
                                        ))))
                        .then(Commands.literal("mutual-friends")
                                .executes(context -> sendConfigCurrent(
                                        context.getSource(),
                                        Config.TownyDisableRatingMutualFriends,
                                        "label.pvprating.config.towny.mutual_friends"
                                ))
                                .then(Commands.argument("enabled", StringArgumentType.word())
                                        .suggests(PvPRatingCommands::suggestBoolean)
                                        .executes(context -> setBooleanConfig(
                                                context.getSource(),
                                                Config.TownyDisableRatingMutualFriends,
                                                StringArgumentType.getString(context, "enabled"),
                                                "label.pvprating.config.towny.mutual_friends",
                                                null
                                        ))))));
    }

    private static boolean isAdmin(CommandSourceStack source) {
        return source.hasPermission(ADMIN_PERMISSION_LEVEL);
    }

    private static CompletableFuture<Suggestions> suggestBoolean(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(new String[]{"true", "false"}, builder);
    }

    private static CompletableFuture<Suggestions> suggestKnownPlayers(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(RatingLeaderboardData.knownPlayerNames(context.getSource().getServer()), builder);
    }

    private static int sendModDescription(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("message.pvprating.command.description"), false);
        return 1;
    }

    private static int sendUsage(CommandSourceStack source, String usageTranslationKey) {
        source.sendSuccess(() -> Component.translatable(usageTranslationKey), false);
        return 1;
    }

    private static int sendTop(CommandSourceStack source, boolean onlineOnly, int page) {
        List<RatingLeaderboardData.RatingEntry> entries = onlineOnly
                ? RatingLeaderboardData.topOnlineEntries(source.getServer())
                : RatingLeaderboardData.topEntries(source.getServer());

        if (entries.isEmpty()) {
            Component emptyMessage = Component.translatable(onlineOnly
                    ? "message.pvprating.command.top.online_empty"
                    : "message.pvprating.command.top.empty");
            source.sendSuccess(() -> emptyMessage, false);
            return 1;
        }

        int totalPages = Math.max(1, (entries.size() + TOP_PAGE_SIZE - 1) / TOP_PAGE_SIZE);
        int selectedPage = Math.min(page, totalPages);
        int startIndex = (selectedPage - 1) * TOP_PAGE_SIZE;
        int endIndex = Math.min(startIndex + TOP_PAGE_SIZE, entries.size());

        Component header = Component.translatable(onlineOnly
                        ? "message.pvprating.command.top.online_header"
                        : "message.pvprating.command.top.header",
                selectedPage,
                totalPages,
                entries.size());
        source.sendSuccess(() -> header, false);

        for (int index = startIndex; index < endIndex; index++) {
            RatingLeaderboardData.RatingEntry entry = entries.get(index);
            Component line = Component.translatable(
                    "message.pvprating.command.top.entry",
                    index + 1,
                    entry.name(),
                    RatingData.formatRating(entry.rating())
            );
            source.sendSuccess(() -> line, false);
        }

        return 1;
    }

    private static int sendPlayerRating(CommandSourceStack source, String playerName) {
        RatingLeaderboardData.RatingEntry entry = RatingLeaderboardData.findEntryByName(source.getServer(), playerName);

        if (entry == null) {
            source.sendFailure(Component.translatable(
                    "message.pvprating.command.rating_not_found",
                    playerName
            ));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(
                "message.pvprating.command.rating_current",
                entry.name(),
                RatingData.formatRating(entry.rating())
        ), false);
        return 1;
    }

    private static RatingTarget ratingTarget(CommandSourceStack source, String playerName) {
        ServerPlayer onlinePlayer = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (onlinePlayer != null) {
            return new RatingTarget(
                    onlinePlayer.getUUID(),
                    onlinePlayer.getGameProfile().getName(),
                    onlinePlayer,
                    RatingData.getRating(onlinePlayer),
                    RatingData.isRatingFrozen(onlinePlayer)
            );
        }

        RatingLeaderboardData.RatingEntry entry = RatingLeaderboardData.findEntryByName(source.getServer(), playerName);
        if (entry == null) {
            source.sendFailure(Component.translatable(
                    "message.pvprating.command.rating_not_found",
                    playerName
            ));
            return null;
        }

        if (!OfflineRatingData.hasPlayerData(source.getServer(), entry.uuid())) {
            source.sendFailure(Component.translatable(
                    "message.pvprating.command.offline_data_not_found",
                    entry.name()
            ));
            return null;
        }

        try {
            return new RatingTarget(
                    entry.uuid(),
                    entry.name(),
                    null,
                    OfflineRatingData.getRating(source.getServer(), entry.uuid()),
                    OfflineRatingData.isRatingFrozen(source.getServer(), entry.uuid())
            );
        } catch (IOException exception) {
            PvPRatingMod.LOGGER.error("Failed to read offline PvPRating data for {}", entry.uuid(), exception);
            source.sendFailure(Component.translatable(
                    "message.pvprating.command.offline_read_failed",
                    entry.name()
            ));
            return null;
        }
    }

    private static int setRatingFromInput(CommandSourceStack source, String playerName, String input) {
        Double parsedRating = parseFiniteDouble(source, input, "label.pvprating.rating");
        if (parsedRating == null) return 0;

        RatingTarget target = ratingTarget(source, playerName);
        if (target == null) return 0;
        return setRating(source, target, parsedRating);
    }

    private static int addRatingFromInput(CommandSourceStack source, String playerName, String input) {
        Double parsedAmount = parseFiniteDoubleRange(
                source,
                input,
                "label.pvprating.command.amount",
                0.0,
                Double.MAX_VALUE
        );
        if (parsedAmount == null) return 0;

        RatingTarget target = ratingTarget(source, playerName);
        if (target == null) return 0;
        return addRating(source, target, parsedAmount);
    }

    private static int subtractRatingFromInput(CommandSourceStack source, String playerName, String input) {
        Double parsedAmount = parseFiniteDoubleRange(
                source,
                input,
                "label.pvprating.command.amount",
                0.0,
                Double.MAX_VALUE
        );
        if (parsedAmount == null) return 0;

        RatingTarget target = ratingTarget(source, playerName);
        if (target == null) return 0;
        return subtractRating(source, target, parsedAmount);
    }

    private static int setRankThresholdFromInput(CommandSourceStack source, String levelInput, String ratingInput) {
        Integer parsedLevel = parseIntegerRange(
                source,
                levelInput,
                "label.pvprating.config.rank.thresholds",
                1,
                MAX_RATING_LEVELS
        );
        if (parsedLevel == null) return 0;
        return setRankThreshold(source, parsedLevel, ratingInput);
    }

    private static int setRating(CommandSourceStack source, RatingTarget target, double rating) {
        if (!isFiniteCommandValue(source, rating)) return 0;

        if (!changeRating(source, target, rating, "set", rating)) return 0;

        source.sendSuccess(() -> Component.translatable(
                "message.pvprating.command.rating_changed",
                target.name()
        ), true);
        return 1;
    }

    private static int addRating(CommandSourceStack source, RatingTarget target, double amount) {
        if (!isFiniteCommandValue(source, amount)) return 0;

        double nextRating = target.rating() + amount;
        if (!isFiniteCommandValue(source, nextRating)) return 0;

        if (!changeRating(source, target, nextRating, "add", amount)) return 0;

        source.sendSuccess(() -> Component.translatable(
                "message.pvprating.command.rating_changed",
                target.name()
        ), true);
        return 1;
    }

    private static int subtractRating(CommandSourceStack source, RatingTarget target, double amount) {
        if (!isFiniteCommandValue(source, amount)) return 0;

        double nextRating = target.rating() - amount;
        if (!isFiniteCommandValue(source, nextRating)) return 0;

        if (!changeRating(source, target, nextRating, "subtract", amount)) return 0;

        source.sendSuccess(() -> Component.translatable(
                "message.pvprating.command.rating_changed",
                target.name()
        ), true);
        return 1;
    }

    private static boolean changeRating(CommandSourceStack source, RatingTarget target, double rating, String action, Double commandAmount) {
        double previousRating = target.rating();

        if (target.onlinePlayer() != null) {
            RatingData.setRating(target.onlinePlayer(), rating);
            target.onlinePlayer().displayClientMessage(Component.translatable(
                    "message.pvprating.rating_changed_manual",
                    RatingData.formatRating(previousRating),
                    RatingData.formatRating(rating)
            ), false);
        } else {
            try {
                OfflineRatingData.setRating(source.getServer(), target.uuid(), rating);
            } catch (IOException exception) {
                PvPRatingMod.LOGGER.error("Failed to write offline PvPRating data for {}", target.uuid(), exception);
                source.sendFailure(Component.translatable(
                        "message.pvprating.command.offline_write_failed",
                        target.name()
                ));
                return false;
            }
            RatingLeaderboardData.putKnown(source.getServer(), target.uuid(), target.name(), rating);
        }

        DisplayEvents.syncAllDisplays(source.getServer());
        RatingAnomalyDetector.analyzeAdminChange(source, action, target.name(), target.uuid(), previousRating, rating, commandAmount);
        RatingAuditLogger.logManualRatingChange(source, action, target.name(), target.uuid(), previousRating, rating, commandAmount);
        return true;
    }

    private static int setFrozen(CommandSourceStack source, String playerName, boolean frozen) {
        RatingTarget target = ratingTarget(source, playerName);
        if (target == null) return 0;

        boolean previousFrozen = target.frozen();
        if (target.onlinePlayer() != null) {
            RatingData.setRatingFrozen(target.onlinePlayer(), frozen);
            target.onlinePlayer().displayClientMessage(Component.translatable(
                    frozen ? "message.pvprating.rating_frozen" : "message.pvprating.rating_unfrozen"
            ), false);
        } else {
            try {
                OfflineRatingData.setRatingFrozen(source.getServer(), target.uuid(), frozen);
            } catch (IOException exception) {
                PvPRatingMod.LOGGER.error("Failed to write offline PvPRating frozen state for {}", target.uuid(), exception);
                source.sendFailure(Component.translatable(
                        "message.pvprating.command.offline_write_failed",
                        target.name()
                ));
                return 0;
            }
        }
        RatingAuditLogger.logManualFrozenChange(source, target.name(), target.uuid(), previousFrozen, frozen);

        source.sendSuccess(() -> Component.translatable(
                frozen ? "message.pvprating.command.rating_frozen" : "message.pvprating.command.rating_unfrozen",
                target.name()
        ), true);
        return 1;
    }

    private static int sendSystemStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(
                "message.pvprating.command.system_status",
                formatConfigValue(Config.System.get())
        ), false);
        return 1;
    }

    private static int sendDisplayStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(
                "message.pvprating.command.display_status",
                formatConfigValue(Config.EnableDisplay.get()),
                Config.DisplayCooldown.get(),
                Config.Color.get(),
                formatConfigValue(Config.Bold.get()),
                formatConfigValue(Config.Italic.get()),
                formatConfigValue(Config.Underlined.get()),
                formatConfigValue(Config.Strikethrough.get())
        ), false);
        return 1;
    }

    private static int sendDisplayStyleStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(
                "message.pvprating.command.display_style_status",
                formatConfigValue(Config.Bold.get()),
                formatConfigValue(Config.Italic.get()),
                formatConfigValue(Config.Underlined.get()),
                formatConfigValue(Config.Strikethrough.get())
        ), false);
        return 1;
    }

    private static int sendRankStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(
                "message.pvprating.command.rank_status",
                formatConfigValue(Config.RankAbsoluteSystem.get()),
                formatRankThresholds(Config.RankThresholds.get())
        ), false);
        return 1;
    }

    private static int sendRankThresholds(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(
                "message.pvprating.command.rank_thresholds",
                formatRankThresholds(Config.RankThresholds.get())
        ), false);
        return 1;
    }

    private static int sendSpawnKillStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(
                "message.pvprating.command.spawnkill_status",
                Config.SpawnKillProtectionSeconds.get(),
                Config.SpawnKillProtectionMaxSeconds.get(),
                RatingData.formatRating(Config.SpawnKillProtectionMultiplier.get())
        ), false);
        return 1;
    }

    private static int sendCombatPowerStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(
                "message.pvprating.command.combat_power_status",
                formatConfigValue(Config.CombatPowerEnabled.get()),
                RatingData.formatRating(Config.CombatPowerMinReadyArmor.get()),
                RatingData.formatRating(Config.CombatPowerMinReadyAttackDamage.get()),
                RatingData.formatRating(Config.CombatPowerArmorWeight.get()),
                RatingData.formatRating(Config.CombatPowerToughnessWeight.get()),
                RatingData.formatRating(Config.CombatPowerAttackDamageWeight.get()),
                RatingData.formatRating(Config.CombatPowerMinGainCoefficient.get()),
                formatConfigValue(Config.CombatPowerReverseAttemptPenaltyEnabled.get())
        ), false);
        return 1;
    }

    private static int sendFormulaStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(
                "message.pvprating.command.formula_status",
                RatingData.formatRating(Config.Gain.get()),
                RatingData.formatRating(Config.Loss.get()),
                RatingData.formatRating(Config.KillerMultiplier.get()),
                RatingData.formatRating(Config.ClaimMultiplier.get()),
                RatingData.formatRating(Config.TargetMultiplier.get()),
                formatConfigValue(Config.LoseCompleteRatingOnDeath.get()),
                formatConfigValue(Config.PreventNegativeRating.get())
        ), false);
        source.sendSuccess(() -> Component.translatable("message.pvprating.command.formula_description"), false);
        return 1;
    }

    private static int sendAnomalyStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(
                "message.pvprating.command.anomaly_status",
                formatConfigValue(Config.RatingAnomalyDetectionEnabled.get()),
                formatConfigValue(Config.RatingAnomalyConsoleAlertsEnabled.get()),
                Config.RatingAnomalyWindowSeconds.get(),
                RatingData.formatRating(Config.RatingAnomalyMaxGainInWindow.get()),
                RatingData.formatRating(Config.RatingAnomalyMaxLossInWindow.get()),
                RatingData.formatRating(Config.RatingAnomalyMaxSingleDelta.get()),
                Config.RatingAnomalyMaxChangesInWindow.get(),
                Config.RatingAnomalyMaxPairChangesInWindow.get(),
                Config.RatingAnomalyAlertCooldownSeconds.get(),
                formatConfigValue(Config.RatingAnomalyTrackAdminChanges.get())
        ), false);
        return 1;
    }

    private static int sendFormulaConfigDetails(
            CommandSourceStack source,
            ForgeConfigSpec.ConfigValue<?> configValue,
            String labelTranslationKey,
            String descriptionTranslationKey
    ) {
        source.sendSuccess(() -> Component.translatable(
                "message.pvprating.command.formula_config_current",
                Component.translatable(labelTranslationKey),
                formatConfigValue(configValue.get()),
                Component.translatable(descriptionTranslationKey)
        ), false);
        return 1;
    }

    private static int sendTownyStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(
                "message.pvprating.command.towny_status",
                formatConfigValue(Config.TownyDisableRatingSameTown.get()),
                formatConfigValue(Config.TownyDisableRatingSameNation.get()),
                formatConfigValue(Config.TownyDisableRatingAlliedNations.get()),
                formatConfigValue(Config.TownyDisableRatingMutualFriends.get())
        ), false);
        return 1;
    }

    private static int sendConfigCurrent(CommandSourceStack source, ForgeConfigSpec.ConfigValue<?> configValue, String labelTranslationKey) {
        source.sendSuccess(() -> Component.translatable(
                "message.pvprating.command.config_current",
                Component.translatable(labelTranslationKey),
                formatConfigValue(configValue.get())
        ), false);
        return 1;
    }

    private static int setBooleanConfig(
            CommandSourceStack source,
            ForgeConfigSpec.BooleanValue configValue,
            String input,
            String labelTranslationKey,
            Runnable afterApply
    ) {
        Boolean parsedValue = parseBoolean(source, input, labelTranslationKey);
        if (parsedValue == null) return 0;
        return setConfigValue(source, configValue, parsedValue, labelTranslationKey, afterApply);
    }

    private static int setIntegerConfig(
            CommandSourceStack source,
            ForgeConfigSpec.ConfigValue<Integer> configValue,
            String input,
            String labelTranslationKey,
            int minValue,
            int maxValue,
            Runnable afterApply
    ) {
        Integer parsedValue = parseIntegerRange(source, input, labelTranslationKey, minValue, maxValue);
        if (parsedValue == null) return 0;
        return setConfigValue(source, configValue, parsedValue, labelTranslationKey, afterApply);
    }

    private static int setDoubleConfig(
            CommandSourceStack source,
            ForgeConfigSpec.ConfigValue<Double> configValue,
            String input,
            String labelTranslationKey,
            Runnable afterApply
    ) {
        Double parsedValue = parseFiniteDouble(source, input, labelTranslationKey);
        if (parsedValue == null) return 0;
        return setConfigValue(source, configValue, parsedValue, labelTranslationKey, afterApply);
    }

    private static int setDoubleConfigRange(
            CommandSourceStack source,
            ForgeConfigSpec.ConfigValue<Double> configValue,
            String input,
            String labelTranslationKey,
            double minValue,
            double maxValue,
            Runnable afterApply
    ) {
        Double parsedValue = parseFiniteDoubleRange(source, input, labelTranslationKey, minValue, maxValue);
        if (parsedValue == null) return 0;
        return setConfigValue(source, configValue, parsedValue, labelTranslationKey, afterApply);
    }

    private static int setRankThreshold(CommandSourceStack source, int level, String input) {
        Double parsedValue = parseFiniteDoubleRange(
                source,
                input,
                "label.pvprating.config.rank.thresholds",
                0.0,
                Double.MAX_VALUE
        );
        if (parsedValue == null) return 0;

        List<Double> nextThresholds = normalizedRankThresholds();
        nextThresholds.set(level - 1, parsedValue);

        if (!isRankThresholdOrderValid(nextThresholds)) {
            source.sendFailure(Component.translatable(
                    "message.pvprating.command.rank_threshold_order_invalid",
                    level
            ));
            return 0;
        }

        return setConfigValue(
                source,
                Config.RankThresholds,
                nextThresholds,
                "label.pvprating.config.rank.thresholds",
                () -> syncAllDisplays(source)
        );
    }

    private static <T> int setConfigValue(
            CommandSourceStack source,
            ForgeConfigSpec.ConfigValue<T> configValue,
            T nextValue,
            String labelTranslationKey,
            Runnable afterApply
    ) {
        T previousValue = configValue.get();

        try {
            configValue.set(nextValue);
            runAfterApply(afterApply);
        } catch (RuntimeException exception) {
            PvPRatingMod.LOGGER.error("Failed to apply config value: key={} previous={} requested={}",
                    labelTranslationKey, previousValue, nextValue, exception);
            restoreConfigValue(configValue, previousValue, labelTranslationKey, afterApply);
            source.sendFailure(Component.translatable(
                    "message.pvprating.command.config_apply_failed",
                    Component.translatable(labelTranslationKey)
            ));
            return 0;
        }

        try {
            Config.SPEC.save();
        } catch (RuntimeException exception) {
            PvPRatingMod.LOGGER.error("Failed to save config value: key={} previous={} requested={}",
                    labelTranslationKey, previousValue, nextValue, exception);
            boolean restored = restoreConfigValue(configValue, previousValue, labelTranslationKey, afterApply);
            source.sendFailure(Component.translatable(
                    restored
                            ? "message.pvprating.command.config_save_failed_restored"
                            : "message.pvprating.command.config_save_failed_not_restored",
                    Component.translatable(labelTranslationKey),
                    formatConfigValue(previousValue),
                    formatConfigValue(nextValue)
            ));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(
                "message.pvprating.command.config_changed",
                Component.translatable(labelTranslationKey),
                formatConfigValue(previousValue),
                formatConfigValue(nextValue)
        ), true);
        RatingAuditLogger.logConfigChange(source, labelTranslationKey, previousValue, nextValue);
        return 1;
    }

    private static <T> boolean restoreConfigValue(
            ForgeConfigSpec.ConfigValue<T> configValue,
            T previousValue,
            String labelTranslationKey,
            Runnable afterApply
    ) {
        try {
            configValue.set(previousValue);
            runAfterApply(afterApply);
            return true;
        } catch (RuntimeException exception) {
            PvPRatingMod.LOGGER.error("Failed to restore config value: key={} previous={}",
                    labelTranslationKey, previousValue, exception);
            return false;
        }
    }

    private static void runAfterApply(Runnable afterApply) {
        if (afterApply != null) afterApply.run();
    }

    private static Boolean parseBoolean(CommandSourceStack source, String input, String labelTranslationKey) {
        if ("true".equalsIgnoreCase(input)) return true;
        if ("false".equalsIgnoreCase(input)) return false;

        source.sendFailure(Component.translatable(
                "message.pvprating.command.invalid_boolean",
                Component.translatable(labelTranslationKey)
        ));
        return null;
    }

    private static Integer parseIntegerRange(CommandSourceStack source, String input, String labelTranslationKey, int minValue, int maxValue) {
        long parsedValue;

        try {
            parsedValue = Long.parseLong(input);
        } catch (NumberFormatException exception) {
            source.sendFailure(Component.translatable(
                    "message.pvprating.command.invalid_integer_range",
                    Component.translatable(labelTranslationKey),
                    minValue,
                    maxValue
            ));
            return null;
        }

        if (parsedValue < minValue || parsedValue > maxValue) {
            source.sendFailure(Component.translatable(
                    "message.pvprating.command.invalid_integer_range",
                    Component.translatable(labelTranslationKey),
                    minValue,
                    maxValue
            ));
            return null;
        }

        return (int) parsedValue;
    }

    private static Double parseFiniteDouble(CommandSourceStack source, String input, String labelTranslationKey) {
        double parsedValue;

        try {
            parsedValue = Double.parseDouble(input.replace(',', '.'));
        } catch (NumberFormatException exception) {
            source.sendFailure(Component.translatable(
                    "message.pvprating.command.invalid_decimal",
                    Component.translatable(labelTranslationKey)
            ));
            return null;
        }

        if (!Double.isFinite(parsedValue)) {
            source.sendFailure(Component.translatable(
                    "message.pvprating.command.invalid_decimal",
                    Component.translatable(labelTranslationKey)
            ));
            return null;
        }

        return parsedValue;
    }

    private static Double parseFiniteDoubleRange(CommandSourceStack source, String input, String labelTranslationKey, double minValue, double maxValue) {
        Double parsedValue = parseFiniteDouble(source, input, labelTranslationKey);
        if (parsedValue == null) return null;

        if (parsedValue < minValue || parsedValue > maxValue) {
            source.sendFailure(Component.translatable(
                    "message.pvprating.command.invalid_decimal_range",
                    Component.translatable(labelTranslationKey),
                    RatingData.formatRating(minValue),
                    RatingData.formatRating(maxValue)
            ));
            return null;
        }

        return parsedValue;
    }

    private static List<Double> normalizedRankThresholds() {
        List<? extends Double> configuredThresholds = Config.RankThresholds.get();
        List<Double> thresholds = new ArrayList<>(MAX_RATING_LEVELS);

        for (int index = 0; index < MAX_RATING_LEVELS; index++) {
            double fallback = (200.0 * index) / (MAX_RATING_LEVELS - 1);
            double threshold = index < configuredThresholds.size() ? configuredThresholds.get(index) : fallback;
            thresholds.add(Double.isFinite(threshold) && threshold >= 0.0 ? threshold : fallback);
        }

        return thresholds;
    }

    private static boolean isRankThresholdOrderValid(List<Double> thresholds) {
        for (int index = 1; index < thresholds.size(); index++) {
            if (thresholds.get(index) < thresholds.get(index - 1)) return false;
        }

        return true;
    }

    private static String formatRankThresholds(List<? extends Double> thresholds) {
        StringJoiner joiner = new StringJoiner(", ");

        for (int index = 0; index < Math.min(MAX_RATING_LEVELS, thresholds.size()); index++) {
            joiner.add((index + 1) + "=" + RatingData.formatRating(thresholds.get(index)));
        }

        return joiner.toString();
    }

    private static Object formatConfigValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return Component.translatable(booleanValue ? "label.pvprating.boolean.true" : "label.pvprating.boolean.false");
        }

        if (value instanceof Double doubleValue) {
            return RatingData.formatRating(doubleValue);
        }

        return String.valueOf(value);
    }

    private static void syncAllDisplays(CommandSourceStack source) {
        DisplayEvents.syncAllDisplays(source.getServer());
    }

    private static boolean isFiniteCommandValue(CommandSourceStack source, double value) {
        if (Double.isFinite(value)) return true;

        source.sendFailure(Component.translatable("message.pvprating.command.invalid_rating"));
        return false;
    }

    private record RatingTarget(UUID uuid, String name, ServerPlayer onlinePlayer, double rating, boolean frozen) {
    }
}
