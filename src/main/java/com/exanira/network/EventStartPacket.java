package com.exanira.network;

import com.exanira.ExaniraMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → Client.
 * Opens the event screen with dialogue and pre-computed choice availability.
 * Stat checks are done server-side; the client just renders what it's told.
 */
public record EventStartPacket(String instanceKey, List<String> dialogue, List<ChoiceData> choices)
        implements CustomPacketPayload {

    public static final Type<EventStartPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ExaniraMod.MODID, "event_start"));

    public static final StreamCodec<FriendlyByteBuf, EventStartPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUtf(packet.instanceKey());
                buf.writeVarInt(packet.dialogue().size());
                for (String line : packet.dialogue()) buf.writeUtf(line);
                buf.writeVarInt(packet.choices().size());
                for (ChoiceData c : packet.choices()) {
                    buf.writeUtf(c.text());
                    buf.writeBoolean(c.available());
                    buf.writeUtf(c.lockedText());
                    buf.writeUtf(c.requirementText());
                }
            },
            buf -> {
                String key = buf.readUtf();
                int dcount = buf.readVarInt();
                List<String> dialogue = new ArrayList<>(dcount);
                for (int i = 0; i < dcount; i++) dialogue.add(buf.readUtf());
                int count = buf.readVarInt();
                List<ChoiceData> choices = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    choices.add(new ChoiceData(buf.readUtf(), buf.readBoolean(), buf.readUtf(), buf.readUtf()));
                }
                return new EventStartPacket(key, dialogue, choices);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * @param requirementText  e.g. "[PER 3+]" — shown on the button regardless of availability
     *                         so players know which stat they are exercising
     */
    public record ChoiceData(String text, boolean available, String lockedText, String requirementText) {}
}
