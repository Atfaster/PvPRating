package dev.pvprating.events;

import dev.pvprating.configs.Config;
import dev.pvprating.network.Packets;
import dev.pvprating.utils.GetValues;
import dev.pvprating.utils.Cooldowns;
import dev.pvprating.utils.RatingLeaderboardData;
import dev.pvprating.utils.RatingData;
import dev.pvprating.PvPRatingMod;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.event.TickEvent.ServerTickEvent;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

@EventBusSubscriber
public class DisplayEvents {

    @SubscribeEvent
    public static void renderName(PlayerEvent.NameFormat event) {
        event.setDisplayname(GetValues.name(event.getEntity()));
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player oldPlayer = event.getOriginal();
        Player newPlayer = event.getEntity();

        if (!Config.System.get()) return;
        if (oldPlayer.getPersistentData().contains(RatingData.RATING_KEY)) {
            RatingData.setRating(newPlayer, RatingData.getRating(oldPlayer));
        }

        if (oldPlayer.getPersistentData().contains(RatingData.RATING_FROZEN_KEY)) {
            RatingData.setRatingFrozen(newPlayer, RatingData.isRatingFrozen(oldPlayer));
        }

        if (newPlayer instanceof ServerPlayer serverPlayer) {
            Cooldowns.runLater(1, () -> syncAllDisplays(serverPlayer.getServer()));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        RatingLeaderboardData.updatePlayer(player);
        Cooldowns.runLater(1, () -> syncAllDisplays(player.getServer()));
    }

    //Tick -> networking -> refreshDisplayName -> renderName
    @SubscribeEvent
    public static void tick(ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        int playerCount = event.getServer().getPlayerCount();
        if (playerCount == 0) return;

        ServerPlayer player = event.getServer().getPlayerList().getPlayers().get(RandomSource.create().nextInt(0, playerCount));
        if (player == null) return;
        if (Cooldowns.isPlayerInCooldown(player.getUUID())) return;

        syncDisplay(player);
    }

    public static void syncDisplay(ServerPlayer player) {
        if (Config.System.get()) {
            GetValues.playerRating.put(player.getUUID(), RatingData.getRating(player));
            RatingLeaderboardData.updatePlayer(player);
        }

        MinecraftServer server = player.getServer();
        double maxKnownRating = server == null ? 0.0 : RatingLeaderboardData.maxKnownRating(server);
        GetValues.maxKnownRating = maxKnownRating;

        PvPRatingMod.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new Packets(
                player.getId(),
                Config.EnableDisplay.get() ? GetValues.playerRating.get(player.getUUID()) : null,
                maxKnownRating
        ));

        player.refreshDisplayName();
    }

    public static void syncAllDisplays(MinecraftServer server) {
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncDisplay(player);
        }
    }
}
