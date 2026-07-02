package dev.pvprating.utils;

import static dev.pvprating.utils.mods.PvPRatingUtils.DefaultDisplay;
import static dev.pvprating.utils.mods.PvPRatingUtils.rankName;
import dev.pvprating.configs.Config;

import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GetValues {
    public static Map<UUID, Double> playerRating = new HashMap<>();
    public static double maxKnownRating = 0.0;

    public static MutableComponent name(Player player) {
        if (Config.EnableDisplay.get() && playerRating.containsKey(player.getUUID())) {
            double rating = playerRating.get(player.getUUID());
            MutableComponent component = Component.empty();
            component.append(rankName(rating));
            component.append(" ");
            component.append(player.getName().getString());
            component.append(DefaultDisplay(player));
            return component;
        }

        return Component.literal(player.getName().getString());
    }
}
