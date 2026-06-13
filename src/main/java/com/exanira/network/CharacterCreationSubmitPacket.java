package com.exanira.network;

import com.exanira.ExaniraMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Client → Server.
 * Sent when the player completes CharacterCreationScreen and clicks "Begin Your Story".
 * Contains the chosen profession index and one option index per lifestyle question.
 */
public record CharacterCreationSubmitPacket(int professionOrdinal, List<Integer> lifestyleChoices)
        implements CustomPacketPayload {

    public static final Type<CharacterCreationSubmitPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ExaniraMod.MODID, "character_creation_submit"));

    public static final StreamCodec<FriendlyByteBuf, CharacterCreationSubmitPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeVarInt(packet.professionOrdinal());
                        buf.writeVarInt(packet.lifestyleChoices().size());
                        packet.lifestyleChoices().forEach(buf::writeVarInt);
                    },
                    buf -> {
                        int profession = buf.readVarInt();
                        int count = buf.readVarInt();
                        List<Integer> choices = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            choices.add(buf.readVarInt());
                        }
                        return new CharacterCreationSubmitPacket(profession, choices);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
