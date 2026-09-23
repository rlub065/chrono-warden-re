package com.chrono;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncPacket {
    public final int charge;
    public final boolean standActive;
    public final boolean blocking;
    public final int anchorLeft;

    public SyncPacket(int charge, boolean standActive, boolean blocking, int anchorLeft) {
        this.charge = charge;
        this.standActive = standActive;
        this.blocking = blocking;
        this.anchorLeft = anchorLeft;
    }

    public static void encode(SyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.charge);
        buf.writeBoolean(msg.standActive);
        buf.writeBoolean(msg.blocking);
        buf.writeVarInt(msg.anchorLeft);
    }

    public static SyncPacket decode(FriendlyByteBuf buf) {
        return new SyncPacket(buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
    }

    public static void handle(SyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ChronoClient.applySync(msg)));
        ctx.get().setPacketHandled(true);
    }
}
