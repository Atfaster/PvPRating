package dev.pvprating.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class RatingLeaderboardData extends SavedData {
    private static final String DATA_NAME = "pvprating_leaderboard";
    private static final String PLAYERS_KEY = "players";
    private static final String UUID_KEY = "uuid";
    private static final String NAME_KEY = "name";
    private static final String RATING_KEY = "rating";

    private final Map<UUID, RatingEntry> entries = new HashMap<>();

    public static RatingLeaderboardData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                RatingLeaderboardData::load,
                RatingLeaderboardData::new,
                DATA_NAME
        );
    }

    public static boolean updatePlayer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        return get(server).put(
                player.getUUID(),
                player.getGameProfile().getName(),
                RatingData.getRating(player)
        );
    }

    public static double maxKnownRating(MinecraftServer server) {
        return get(server).maxRating();
    }

    public static List<RatingEntry> topEntries(MinecraftServer server) {
        return get(server).positiveEntries();
    }

    public static List<RatingEntry> topOnlineEntries(MinecraftServer server) {
        RatingLeaderboardData data = get(server);
        List<RatingEntry> onlineEntries = new ArrayList<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            RatingEntry entry = data.updateOnlinePlayer(player);
            if (entry != null && isPositiveFinite(entry.rating())) onlineEntries.add(entry);
        }

        return sorted(onlineEntries);
    }

    public static RatingEntry findEntryByName(MinecraftServer server, String name) {
        RatingLeaderboardData data = get(server);
        data.updateOnlinePlayers(server);

        return data.findByName(name);
    }

    public static List<String> knownPlayerNames(MinecraftServer server) {
        RatingLeaderboardData data = get(server);
        data.updateOnlinePlayers(server);

        List<String> names = new ArrayList<>();
        for (RatingEntry entry : data.entries.values()) {
            names.add(entry.name());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static void putKnown(MinecraftServer server, UUID uuid, String name, double rating) {
        get(server).put(uuid, name, rating);
    }

    private RatingEntry findByName(String name) {
        for (RatingEntry entry : entries.values()) {
            if (entry.name().equalsIgnoreCase(name)) return entry;
        }

        return null;
    }

    private void updateOnlinePlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            updateOnlinePlayer(player);
        }
    }

    private RatingEntry updateOnlinePlayer(ServerPlayer player) {
        put(player.getUUID(), player.getGameProfile().getName(), RatingData.getRating(player));
        return entries.get(player.getUUID());
    }

    private static RatingLeaderboardData load(CompoundTag tag) {
        RatingLeaderboardData data = new RatingLeaderboardData();
        ListTag players = tag.getList(PLAYERS_KEY, Tag.TAG_COMPOUND);

        for (int index = 0; index < players.size(); index++) {
            CompoundTag playerTag = players.getCompound(index);
            if (!playerTag.hasUUID(UUID_KEY)) continue;

            UUID uuid = playerTag.getUUID(UUID_KEY);
            String name = playerTag.getString(NAME_KEY);
            double rating = playerTag.getDouble(RATING_KEY);
            if (!Double.isFinite(rating)) continue;

            data.entries.put(uuid, new RatingEntry(uuid, safeName(name, uuid), rating));
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();

        for (RatingEntry entry : entries.values()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID(UUID_KEY, entry.uuid());
            playerTag.putString(NAME_KEY, entry.name());
            playerTag.putDouble(RATING_KEY, entry.rating());
            players.add(playerTag);
        }

        tag.put(PLAYERS_KEY, players);
        return tag;
    }

    private boolean put(UUID uuid, String name, double rating) {
        if (!Double.isFinite(rating)) return false;

        double previousMaxRating = maxRating();
        RatingEntry previousEntry = entries.get(uuid);
        RatingEntry nextEntry = new RatingEntry(uuid, safeName(name, uuid), rating);

        if (previousEntry != null
                && Double.compare(previousEntry.rating(), nextEntry.rating()) == 0
                && previousEntry.name().equals(nextEntry.name())) {
            return false;
        }

        entries.put(uuid, nextEntry);
        setDirty();
        return Double.compare(previousMaxRating, maxRating()) != 0;
    }

    private double maxRating() {
        double maxRating = 0.0;

        for (RatingEntry entry : entries.values()) {
            if (isPositiveFinite(entry.rating())) maxRating = Math.max(maxRating, entry.rating());
        }

        return maxRating;
    }

    private List<RatingEntry> positiveEntries() {
        List<RatingEntry> positiveEntries = new ArrayList<>();

        for (RatingEntry entry : entries.values()) {
            if (isPositiveFinite(entry.rating())) positiveEntries.add(entry);
        }

        return sorted(positiveEntries);
    }

    private static List<RatingEntry> sorted(List<RatingEntry> entries) {
        entries.sort(Comparator
                .comparingDouble(RatingEntry::rating)
                .reversed()
                .thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT))
                .thenComparing(entry -> entry.uuid().toString()));
        return entries;
    }

    private static boolean isPositiveFinite(double rating) {
        return Double.isFinite(rating) && rating > 0.0;
    }

    private static String safeName(String name, UUID uuid) {
        if (name == null || name.isBlank()) return uuid.toString();
        return name;
    }

    public record RatingEntry(UUID uuid, String name, double rating) {
    }
}
