package com.example.exanira.network;

import com.example.exanira.ExaniraMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client.
 * Sent when a player logs in without an initialized CharacterSheet.
 * The client responds by opening CharacterCreationScreen.
 * Carries no data — it is purely a trigger signal.
 */
public record OpenCharacterCreationPacket() implements CustomPacketPayload {

    public static final Type<OpenCharacterCreationPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ExaniraMod.MODID, "open_character_creation"));

    public static final StreamCodec<FriendlyByteBuf, OpenCharacterCreationPacket> STREAM_CODEC =
            StreamCodec.unit(new OpenCharacterCreationPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
