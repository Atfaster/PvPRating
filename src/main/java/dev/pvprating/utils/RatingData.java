package dev.pvprating.utils;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class RatingData {
    public static final String RATING_KEY = "rating";
    public static final String RATING_FROZEN_KEY = "ratingFrozen";

    public static double getRating(Player player) {
        return player.getPersistentData().getDouble(RATING_KEY);
    }

    public static boolean setRating(Player player, double rating) {
        player.getPersistentData().putDouble(RATING_KEY, rating);
        GetValues.playerRating.put(player.getUUID(), rating);

        if (player instanceof ServerPlayer serverPlayer) {
            return RatingLeaderboardData.updatePlayer(serverPlayer);
        }

        return false;
    }

    public static boolean isRatingFrozen(Player player) {
        return player.getPersistentData().getBoolean(RATING_FROZEN_KEY);
    }

    public static void setRatingFrozen(Player player, boolean frozen) {
        player.getPersistentData().putBoolean(RATING_FROZEN_KEY, frozen);
    }

    public static String formatSignedRating(double value) {
        String sign = value > 0 ? "+" : "-";
        return sign + formatRating(Math.abs(value));
    }

    public static String formatRating(double value) {
        if (!Double.isFinite(value)) return String.valueOf(value);

        BigDecimal roundedValue = BigDecimal.valueOf(value)
                .setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros();

        if (roundedValue.compareTo(BigDecimal.ZERO) == 0) return "0";
        return roundedValue.toPlainString();
    }
}
