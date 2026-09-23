package com.chrono;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class KeyPacket {
    private final int key;
    private final int action;
    private final int data; // for cannon = hold ticks, for others = shift flag

    public KeyPacket(int key, int action, int data) {
        this.key = key;
        this.action = action;
        this.data = data;
    }

    public static void encode(KeyPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.key);
        buf.writeByte(msg.action);
        buf.writeVarInt(msg.data);
    }

    public static KeyPacket decode(FriendlyByteBuf buf) {
        return new KeyPacket(buf.readByte(), buf.readByte(), buf.readVarInt());
    }

    public static void handle(KeyPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer p = c.getSender();
            if (p != null) ChronoManager.handleKey(p, msg.key, msg.action, msg.data);
        });
        c.setPacketHandled(true);
    }
}
