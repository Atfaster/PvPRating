package dev.pvprating.utils;

import dev.pvprating.PvPRatingMod;
import dev.pvprating.configs.Config;

import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.TickEvent;


import java.util.*;

@EventBusSubscriber
public class Cooldowns {

    /// Tick timer
    private static class Delay {
        int ticks;
        Runnable methods;

        Delay(int ticks, Runnable methods) {
            this.ticks = ticks;
            this.methods = methods;
        }
    }

    private static final List<Delay> tasks = new ArrayList<>();
    private static final List<Delay> wait = new ArrayList<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (!wait.isEmpty()) {
            tasks.addAll(wait);
            wait.clear();
        }

        Iterator<Delay> it = tasks.iterator();
        while (it.hasNext()) {
            Delay task = it.next();
            task.ticks--;
            if (task.ticks <= 0) {

                try {
                    task.methods.run();
                } catch (Exception exception) {
                    PvPRatingMod.LOGGER.error("PvPRating delayed task failed.", exception);
                }

                it.remove();
            }
        }
    }

    public static void runLater(int ticks, Runnable methods) {
        if (methods == null) {
            return;
        }
        wait.add(new Delay(ticks, methods));
    }

    /// Player cooldown
    public static Map<UUID, Long> playerCooldownHashMap = new HashMap<>();

    public static Boolean isPlayerInCooldown(UUID player) {
        if (playerCooldownHashMap.containsKey(player) && System.currentTimeMillis() < playerCooldownHashMap.get(player)) return true;
        playerCooldownHashMap.put(player, System.currentTimeMillis() + (Config.DisplayCooldown.get() * 50L));
        return false;
    }
}
