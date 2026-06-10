package com.example.exanira.network;

import com.example.exanira.ExaniraMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client.
 * Tells the client to close the event HUD for the given instance key.
 */
public record EventEndPacket(String instanceKey)
        implements CustomPacketPayload {

    public static final Type<EventEndPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ExaniraMod.MODID, "event_end"));

    public static final StreamCodec<FriendlyByteBuf, EventEndPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.instanceKey()),
            buf -> new EventEndPacket(buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
