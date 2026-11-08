package com.echobroadcast.beyondthebag.network;

import com.echobroadcast.beyondthebag.world.PocketTravel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class BagActionPacket {
    private final int action;
    public BagActionPacket(int action) { this.action = action; }
    public static void encode(BagActionPacket packet, FriendlyByteBuf buf) { buf.writeVarInt(packet.action); }
    public static BagActionPacket decode(FriendlyByteBuf buf) { return new BagActionPacket(buf.readVarInt()); }
    public static void handle(BagActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                switch (packet.action) {
                    case 0 -> PocketTravel.enterHome(player);
                    case 1 -> PocketTravel.enterMaze(player);
                    case 2 -> PocketTravel.leavePocket(player);
                    default -> { }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
