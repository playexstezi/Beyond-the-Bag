package com.echobroadcast.beyondthebag.network;

import com.echobroadcast.beyondthebag.BeyondTheBag;
import com.echobroadcast.beyondthebag.world.PocketTravel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class BagNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BeyondTheBag.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private BagNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, BagActionPacket.class, BagActionPacket::encode,
                BagActionPacket::decode, BagActionPacket::handle);
    }

    public static void sendAction(int action) {
        CHANNEL.sendToServer(new BagActionPacket(action));
    }

    public static void sendTo(ServerPlayer player, BagActionPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
