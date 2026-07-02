package dev.pvprating.events;

import static dev.pvprating.configs.Config.*;
import dev.pvprating.PvPRatingMod;
import dev.pvprating.compats.TownyCompat;
import dev.pvprating.utils.RatingAnomalyDetector;
import dev.pvprating.utils.RatingAuditLogger;
import dev.pvprating.utils.RatingData;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.api.distmarker.Dist;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(Dist.DEDICATED_SERVER)
public class PlayerDeathEvent {
    private static final Map<UUID, Map<UUID, PairKillState>> pairKillStates = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        Entity source = event.getSource().getEntity();

        if (!(source instanceof ServerPlayer killer)) return;
        if (!(entity instanceof ServerPlayer target)) return;

        if (killer == target) return;
        if (!target.gameMode.isSurvival() || !killer.gameMode.isSurvival()) return;

        if (System.get()) {
            RatingDecision ratingDecision = ratingDecision(killer, target);
            if (ratingDecision.applyRatingChange()) {
                double killerPreviousRating = RatingData.getRating(killer);
                double targetPreviousRating = RatingData.getRating(target);
                double killerCurrentRating = killerPreviousRating;
                double targetCurrentRating = targetPreviousRating;
                boolean killerFrozen = RatingData.isRatingFrozen(killer);
                boolean targetFrozen = RatingData.isRatingFrozen(target);

                if (killerFrozen) {
                    sendFrozenRatingSkipMessage(killer);
                } else {
                    killerCurrentRating = handleKiller(killer, target, ratingDecision.ratingCoefficient());
                }

                if (targetFrozen) {
                    sendFrozenRatingSkipMessage(target);
                } else {
                    targetCurrentRating = handleTarget(target, ratingDecision.ratingCoefficient());
                }

                RatingAuditLogger.logRatingKill(
                        killer,
                        target,
                        killerPreviousRating,
                        killerCurrentRating,
                        Double.compare(killerPreviousRating, killerCurrentRating) != 0,
                        killerFrozen,
                        targetPreviousRating,
                        targetCurrentRating,
                        Double.compare(targetPreviousRating, targetCurrentRating) != 0,
                        targetFrozen
                );
                RatingAnomalyDetector.analyzeKill(
                        killer,
                        target,
                        killerPreviousRating,
                        killerCurrentRating,
                        Double.compare(killerPreviousRating, killerCurrentRating) != 0,
                        targetPreviousRating,
                        targetCurrentRating,
                        Double.compare(targetPreviousRating, targetCurrentRating) != 0
                );

                DisplayEvents.syncAllDisplays(killer.getServer());
            }
        }
    }

    private static RatingDecision ratingDecision(ServerPlayer killer, ServerPlayer target) {
        String townySkipReason = townyRatingSkipReason(killer, target);
        if (townySkipReason != null) {
            Map<String, Object> details = new HashMap<>();
            details.put("townyReason", townySkipReason);
            RatingAuditLogger.logSkippedKill(killer, target, "towny", details);
            sendTownySkipMessages(killer, target, townySkipReason);
            return RatingDecision.skip();
        }

        CombatPowerSnapshot killerPower = CombatPowerSnapshot.of(killer);
        CombatPowerSnapshot targetPower = CombatPowerSnapshot.of(target);
        double gainCoefficient = combatPowerGainCoefficient(killerPower, targetPower);
        gainCoefficient *= reverseAttemptPenaltyCoefficient(killer, target);

        int protectionSeconds = SpawnKillProtectionSeconds.get();
        if (protectionSeconds <= 0) {
            clearReverseAttemptPenalty(killer, target);
            return RatingDecision.apply(gainCoefficient);
        }

        long now = java.lang.System.currentTimeMillis();
        Map<UUID, PairKillState> victimKillStates = pairKillStates.computeIfAbsent(killer.getUUID(), uuid -> new HashMap<>());
        PairKillState state = victimKillStates.get(target.getUUID());

        if (state == null) {
            victimKillStates.put(target.getUUID(), PairKillState.initial(now, protectionSeconds));
            clearReverseAttemptPenalty(killer, target);
            return RatingDecision.apply(gainCoefficient);
        }

        long elapsedMillis = now - state.lastKillTimeMillis();
        if (elapsedMillis >= state.currentCooldownSeconds() * 1000L) {
            victimKillStates.put(target.getUUID(), PairKillState.initial(now, protectionSeconds));
            clearReverseAttemptPenalty(killer, target);
            return RatingDecision.apply(gainCoefficient);
        }

        if (targetPower.ready()) {
            victimKillStates.put(target.getUUID(), PairKillState.initial(now, protectionSeconds));
            clearReverseAttemptPenalty(killer, target);
            return RatingDecision.apply(gainCoefficient);
        }

        PairKillState escalatedState = state.escalate(now, protectionSeconds);
        victimKillStates.put(target.getUUID(), escalatedState);

        Map<String, Object> details = new HashMap<>();
        details.put("elapsedSeconds", elapsedMillis / 1000L);
        details.put("requiredSeconds", state.currentCooldownSeconds());
        details.put("nextRequiredSeconds", escalatedState.currentCooldownSeconds());
        details.put("violations", escalatedState.violations());
        details.put("killerCombatPower", killerPower.power());
        details.put("victimCombatPower", targetPower.power());
        details.put("victimReady", targetPower.ready());
        RatingAuditLogger.logSkippedKill(killer, target, "spawn_kill_protection", details);
        sendSpawnKillSkipMessages(killer, target, elapsedMillis / 1000L, state.currentCooldownSeconds(), escalatedState.currentCooldownSeconds());
        return RatingDecision.skip();
    }

    private static String townyRatingSkipReason(ServerPlayer killer, ServerPlayer target) {
        try {
            return TownyCompat.ratingSkipReason(killer, target);
        } catch (Exception exception) {
            PvPRatingMod.LOGGER.warn("Towny rating filter failed; default PvP rating logic will continue.", exception);
            return null;
        } catch (LinkageError error) {
            PvPRatingMod.LOGGER.warn("Towny rating filter could not load; default PvP rating logic will continue.", error);
            return null;
        }
    }

    private static void sendTownySkipMessages(ServerPlayer killer, ServerPlayer target, String reasonTranslationKey) {
        Component reason = Component.translatable(reasonTranslationKey);
        killer.displayClientMessage(Component.translatable("message.pvprating.rating_skipped.killer", reason), false);
        target.displayClientMessage(Component.translatable("message.pvprating.rating_skipped.victim", reason), false);
    }

    private static void sendSpawnKillSkipMessages(ServerPlayer killer, ServerPlayer target, long elapsedSeconds, int protectionSeconds, int nextProtectionSeconds) {
        killer.displayClientMessage(Component.translatable("message.pvprating.spawn_kill_skipped.killer", elapsedSeconds, protectionSeconds, nextProtectionSeconds), false);
        target.displayClientMessage(Component.translatable("message.pvprating.spawn_kill_skipped.victim", elapsedSeconds, protectionSeconds, nextProtectionSeconds), false);
    }

    private static void sendFrozenRatingSkipMessage(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.pvprating.rating_skipped.frozen"), false);
    }

    public static double handleKiller(ServerPlayer killer, ServerPlayer target) {
        return handleKiller(killer, target, 1.0);
    }

    public static double handleKiller(ServerPlayer killer, ServerPlayer target, double gainCoefficient) {
        double previousRating = RatingData.getRating(killer);
        double killerRating = previousRating;

        double ratingGain = Gain.get() + (killerRating * KillerMultiplier.get()
                + (RatingData.getRating(target) * ClaimMultiplier.get()));
        killerRating += ratingGain * sanitizeRatingCoefficient(gainCoefficient);

        RatingData.setRating(killer, killerRating);
        sendRatingChangeMessage(killer, previousRating, killerRating);
        return killerRating;
    }

    public static double handleTarget(ServerPlayer target) {
        return handleTarget(target, 1.0);
    }

    public static double handleTarget(ServerPlayer target, double lossCoefficient) {
        double previousRating = RatingData.getRating(target);
        double targetRating = previousRating;
        double ratingCoefficient = sanitizeRatingCoefficient(lossCoefficient);

        if (LoseCompleteRatingOnDeath.get()) {
            double fullLossTargetRating = -Loss.get() - (targetRating * TargetMultiplier.get());
            targetRating += (fullLossTargetRating - targetRating) * ratingCoefficient;
        } else {
            targetRating -= (Loss.get() + (targetRating * TargetMultiplier.get())) * ratingCoefficient;
        }

        if (PreventNegativeRating.get()) {
            targetRating = Math.max(0, targetRating);
        }

        RatingData.setRating(target, targetRating);
        sendRatingChangeMessage(target, previousRating, targetRating);
        return targetRating;
    }

    private static void sendRatingChangeMessage(ServerPlayer player, double previousRating, double currentRating) {
        double delta = currentRating - previousRating;
        if (Double.compare(delta, 0.0) == 0) return;

        player.displayClientMessage(Component.translatable(
                "message.pvprating.rating_changed",
                RatingData.formatSignedRating(delta),
                RatingData.formatRating(currentRating)
        ), false);
    }

    private static double combatPowerGainCoefficient(CombatPowerSnapshot killerPower, CombatPowerSnapshot targetPower) {
        if (!CombatPowerEnabled.get()) return 1.0;
        if (killerPower.power() <= 0.0 || targetPower.power() >= killerPower.power()) return 1.0;

        double coefficient = targetPower.power() / killerPower.power();
        return Math.max(CombatPowerMinGainCoefficient.get(), coefficient);
    }

    private static double reverseAttemptPenaltyCoefficient(ServerPlayer killer, ServerPlayer target) {
        if (!CombatPowerEnabled.get() || !CombatPowerReverseAttemptPenaltyEnabled.get()) return 1.0;

        Map<UUID, PairKillState> reverseStates = pairKillStates.get(target.getUUID());
        if (reverseStates == null) return 1.0;

        PairKillState reverseState = reverseStates.get(killer.getUUID());
        if (reverseState == null || reverseState.violations() <= 0) return 1.0;

        return Math.max(CombatPowerMinGainCoefficient.get(), 1.0 / (reverseState.violations() + 1.0));
    }

    private static void clearReverseAttemptPenalty(ServerPlayer killer, ServerPlayer target) {
        Map<UUID, PairKillState> reverseStates = pairKillStates.get(target.getUUID());
        if (reverseStates == null) return;

        reverseStates.remove(killer.getUUID());
        if (reverseStates.isEmpty()) pairKillStates.remove(target.getUUID());
    }

    private static double sanitizeRatingCoefficient(double ratingCoefficient) {
        if (!Double.isFinite(ratingCoefficient)) return 1.0;
        return Math.max(0.0, Math.min(1.0, ratingCoefficient));
    }

    private record RatingDecision(boolean applyRatingChange, double ratingCoefficient) {
        private static RatingDecision apply(double ratingCoefficient) {
            return new RatingDecision(true, sanitizeRatingCoefficient(ratingCoefficient));
        }

        private static RatingDecision skip() {
            return new RatingDecision(false, 0.0);
        }
    }

    private record CombatPowerSnapshot(double armor, double toughness, double attackDamage, double power, boolean ready) {
        private static CombatPowerSnapshot of(ServerPlayer player) {
            if (!CombatPowerEnabled.get()) return new CombatPowerSnapshot(0.0, 0.0, 0.0, 0.0, false);

            double armor = player.getArmorValue();
            double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            double attackDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            double power = armor * CombatPowerArmorWeight.get()
                    + toughness * CombatPowerToughnessWeight.get()
                    + attackDamage * CombatPowerAttackDamageWeight.get();
            boolean ready = armor >= CombatPowerMinReadyArmor.get()
                    && attackDamage >= CombatPowerMinReadyAttackDamage.get();

            return new CombatPowerSnapshot(armor, toughness, attackDamage, Math.max(0.0, power), ready);
        }
    }

    private record PairKillState(long lastKillTimeMillis, int currentCooldownSeconds, int violations) {
        private static PairKillState initial(long now, int protectionSeconds) {
            return new PairKillState(now, protectionSeconds, 0);
        }

        private PairKillState escalate(long now, int baseProtectionSeconds) {
            int maxSeconds = SpawnKillProtectionMaxSeconds.get();
            if (maxSeconds <= 0) maxSeconds = Integer.MAX_VALUE;

            double multipliedCooldown = currentCooldownSeconds * SpawnKillProtectionMultiplier.get();
            int nextCooldownSeconds = (int) Math.min(maxSeconds, Math.max(baseProtectionSeconds, Math.ceil(multipliedCooldown)));
            return new PairKillState(now, nextCooldownSeconds, violations + 1);
        }
    }
}
