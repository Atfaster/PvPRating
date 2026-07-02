package dev.pvprating.utils.mods;

import static dev.pvprating.configs.Config.*;
import dev.pvprating.utils.GetValues;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.*;

import java.util.List;

public class PvPRatingUtils {
    private static final ResourceLocation RATING_ICON_FONT = ResourceLocation.fromNamespaceAndPath("pvprating", "rating_icons");
    private static final char RATING_ICON_START = '\uE000';
    public static final int MAX_RATING_LEVELS = 10;

    public static MutableComponent DefaultDisplay(Player player) {
        MutableComponent component = Component.empty();
        Style bold = Style.EMPTY.withBold(Bold.get());
        Style italic = Style.EMPTY.withItalic(Italic.get());
        Style underlined = Style.EMPTY.withUnderlined(Underlined.get());
        Style strikethrough = Style.EMPTY.withStrikethrough(Strikethrough.get());
        Style hoverEvent = Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("label.pvprating.rating")));
        Style color = Style.EMPTY.withColor(Color.get());

        double ratingValue = GetValues.playerRating.get(player.getUUID());
        String ratingString = String.valueOf((int) ratingValue);
        MutableComponent rating = Component.literal(ratingString).withStyle(bold).withStyle(italic).withStyle(underlined).withStyle(strikethrough);
        int rankIndex = ratingRankIndex(ratingValue);

        component.append(displayPrefix());
        component.append(ratingIcon(rankIndex));
        component.append(rating.withStyle(color).withStyle(hoverEvent));
        component.append(Display2.get());

        return component;
    }

    public static MutableComponent rankName(double ratingValue) {
        int rankIndex = ratingRankIndex(ratingValue);
        return Component.translatable("rank.pvprating." + (rankIndex + 1));
    }

    private static String displayPrefix() {
        String display = Display1.get();
        if (display.endsWith("$")) return display.substring(0, display.length() - 1);
        return display;
    }

    private static MutableComponent ratingIcon(int rankIndex) {
        Style iconStyle = Style.EMPTY
                .withFont(RATING_ICON_FONT)
                .withColor(0xFFFFFF)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("label.pvprating.rating")));

        return Component.literal(String.valueOf((char) (RATING_ICON_START + rankIndex))).withStyle(iconStyle);
    }

    public static int ratingRankIndex(double ratingValue) {
        if (!Double.isFinite(ratingValue) || ratingValue <= 0.0) return 0;
        if (!RankAbsoluteSystem.get()) return manualRankIndex(ratingValue);

        double maxRating = GetValues.maxKnownRating;
        if (!Double.isFinite(maxRating) || maxRating <= 0.0) return 0;
        if (ratingValue >= maxRating) return MAX_RATING_LEVELS - 1;

        int iconLevel = 1 + (int) Math.ceil((ratingValue * (MAX_RATING_LEVELS - 1)) / maxRating);
        iconLevel = Math.max(2, Math.min(MAX_RATING_LEVELS, iconLevel));
        return iconLevel - 1;
    }

    private static int manualRankIndex(double ratingValue) {
        List<? extends Double> thresholds = RankThresholds.get();
        int highestReachedIndex = 0;

        for (int index = 0; index < Math.min(MAX_RATING_LEVELS, thresholds.size()); index++) {
            double threshold = thresholds.get(index);
            if (!Double.isFinite(threshold)) continue;
            if (ratingValue >= threshold) highestReachedIndex = index;
        }

        return highestReachedIndex;
    }
}
