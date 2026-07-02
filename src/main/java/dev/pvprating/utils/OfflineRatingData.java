package dev.pvprating.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class OfflineRatingData {
    private static final String FORGE_DATA_KEY = "ForgeData";

    public static boolean hasPlayerData(MinecraftServer server, UUID uuid) {
        return Files.isRegularFile(playerDataPath(server, uuid));
    }

    public static double getRating(MinecraftServer server, UUID uuid) throws IOException {
        CompoundTag forgeData = forgeData(readPlayerData(server, uuid));
        return forgeData.getDouble(RatingData.RATING_KEY);
    }

    public static void setRating(MinecraftServer server, UUID uuid, double rating) throws IOException {
        CompoundTag playerData = readPlayerData(server, uuid);
        forgeData(playerData).putDouble(RatingData.RATING_KEY, rating);
        writePlayerData(server, uuid, playerData);
    }

    public static boolean isRatingFrozen(MinecraftServer server, UUID uuid) throws IOException {
        CompoundTag forgeData = forgeData(readPlayerData(server, uuid));
        return forgeData.getBoolean(RatingData.RATING_FROZEN_KEY);
    }

    public static void setRatingFrozen(MinecraftServer server, UUID uuid, boolean frozen) throws IOException {
        CompoundTag playerData = readPlayerData(server, uuid);
        forgeData(playerData).putBoolean(RatingData.RATING_FROZEN_KEY, frozen);
        writePlayerData(server, uuid, playerData);
    }

    private static CompoundTag readPlayerData(MinecraftServer server, UUID uuid) throws IOException {
        Path path = playerDataPath(server, uuid);
        if (!Files.isRegularFile(path)) return new CompoundTag();
        return NbtIo.readCompressed(path.toFile());
    }

    private static void writePlayerData(MinecraftServer server, UUID uuid, CompoundTag playerData) throws IOException {
        Path path = playerDataPath(server, uuid);
        Files.createDirectories(path.getParent());
        NbtIo.writeCompressed(playerData, path.toFile());
    }

    private static CompoundTag forgeData(CompoundTag playerData) {
        if (!playerData.contains(FORGE_DATA_KEY)) {
            playerData.put(FORGE_DATA_KEY, new CompoundTag());
        }
        return playerData.getCompound(FORGE_DATA_KEY);
    }

    private static Path playerDataPath(MinecraftServer server, UUID uuid) {
        return server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat");
    }
}
