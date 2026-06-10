package com.example.exanira.network;

import com.example.exanira.ExaniraMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → Server.
 * Sent when the player clicks an available choice button in the event HUD.
 */
public record EventChoicePacket(String instanceKey, int choiceIndex)
        implements CustomPacketPayload {

    public static final Type<EventChoicePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ExaniraMod.MODID, "event_choice"));

    public static final StreamCodec<FriendlyByteBuf, EventChoicePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.instanceKey());
                buf.writeVarInt(p.choiceIndex());
            },
            buf -> new EventChoicePacket(buf.readUtf(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
