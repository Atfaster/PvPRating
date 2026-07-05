package dev.pvprating.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        Double sanitizedRating = RatingData.sanitizeRating(rating);
        if (sanitizedRating == null) {
            throw new IOException("Refused to store non-finite PvPRating value for " + uuid + ": " + rating);
        }

        CompoundTag playerData = readPlayerData(server, uuid);
        forgeData(playerData).putDouble(RatingData.RATING_KEY, sanitizedRating);
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
        Path temporaryPath = path.resolveSibling(path.getFileName() + ".tmp");

        try {
            NbtIo.writeCompressed(playerData, temporaryPath.toFile());
            backupExistingFile(path);
            moveReplacing(temporaryPath, path);
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    private static void backupExistingFile(Path path) throws IOException {
        if (!Files.isRegularFile(path)) return;
        Files.copy(path, backupPath(path), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path backupPath(Path path) {
        return path.resolveSibling(path.getFileName() + ".bak");
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
