package dev.pvprating.network;

import dev.pvprating.utils.GetValues;

import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkDirection;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public record Packets(int playerID, @Nullable Double rating, double maxKnownRating) {

    public static void encode(Packets packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.playerID);

        buffer.writeBoolean(packet.rating != null);
        if (packet.rating != null) buffer.writeDouble(packet.rating);
        buffer.writeDouble(packet.maxKnownRating);
    }

    public static Packets decode(FriendlyByteBuf buffer) {
        int playerID = buffer.readInt();

        Double rating = buffer.readBoolean() ? buffer.readDouble() : null;
        double maxKnownRating = buffer.readDouble();

        return new Packets(playerID, rating, maxKnownRating);
    }

    public static void handle(Packets packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context.getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
            context.setPacketHandled(true);
            return;
        }

        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null || minecraft.player.connection == null) return;

            Player player = (Player) minecraft.level.getEntity(packet.playerID);
            if (player == null) return;

            GetValues.maxKnownRating = packet.maxKnownRating;

            if (packet.rating != null) {
                GetValues.playerRating.put(player.getUUID(), packet.rating);
            } else {
                GetValues.playerRating.remove(player.getUUID());
            }

            var playerInfo = minecraft.player.connection.getPlayerInfo(player.getUUID());
            if (playerInfo != null) playerInfo.setTabListDisplayName(GetValues.name(player));
            player.refreshDisplayName();
        });

        context.setPacketHandled(true);
    }
}
