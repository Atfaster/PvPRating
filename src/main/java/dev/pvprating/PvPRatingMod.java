package dev.pvprating;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;

import dev.pvprating.configs.*;
import dev.pvprating.utils.InvalidConfigException;
import dev.pvprating.utils.Cooldowns;
import dev.pvprating.network.Packets;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

@SuppressWarnings("removal")
@Mod("pvprating")
@Mod.EventBusSubscriber
public class PvPRatingMod {
    /// 1300 lines of code!
    /// powershell "Get-ChildItem -Recurse -Include  *.java,build.gradle,settings.gradle,gradle.properties,*.mcmeta,mods.toml | Get-Content | Measure-Object -Line"
    public static final Logger LOGGER = LogManager.getLogger("pvprating");
    public static boolean sent = false;
    public static String version = ModList.get().getModFileById("pvprating").versionString();

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("pvprating", version),
            () -> version,
            version::equals,
            version::equals
    );

    public PvPRatingMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        CHANNEL.registerMessage(
                0,
                Packets.class,
                Packets::encode,
                Packets::decode,
                Packets::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void configCheck(ServerStartingEvent event) throws InvalidConfigException {
        InvalidConfigException.CheckInvalidConfigs();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverLoadLoop(ServerStartedEvent event) {
        if (Config.StartupWarning.get()) Cooldowns.runLater(6000, () -> {

            if (event.getServer().getPlayerCount() != 0)
                for (Player player : event.getServer().getPlayerList().getPlayers()) {

                    if (player.hasPermissions(4)) {
                        player.displayClientMessage(Component.translatable("message.pvprating.startup_warning"), false);
                        sent = true;
                    }
                }

            if (!sent) serverLoadLoop(event);
        });
    }
}
