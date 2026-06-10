package com.example.exanira.network;

import com.example.exanira.ExaniraMod;
import com.example.exanira.character.CharacterSheet;
import com.example.exanira.character.Stat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;

/**
 * Server → Client packet. Carries the full character sheet snapshot for display in the GUI.
 * Sent on login (PlayerLoginHandler) and whenever stats change.
 *
 * Handling is registered client-side in ExaniraModClient to avoid loading client classes
 * on a dedicated server.
 */
public record CharacterSheetSyncPacket(Map<Stat, Integer> stats, String backstory)
        implements CustomPacketPayload {

    public static final Type<CharacterSheetSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ExaniraMod.MODID, "character_sheet_sync"));

    /**
     * Stats are encoded in Stat.values() ordinal order as VarInts, followed by the backstory string.
     * This keeps the codec simple and avoids sending stat names over the wire.
     */
    public static final StreamCodec<FriendlyByteBuf, CharacterSheetSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                for (Stat stat : Stat.values()) {
                    buf.writeVarInt(packet.stats().getOrDefault(stat, 1));
                }
                buf.writeUtf(packet.backstory());
            },
            buf -> {
                Map<Stat, Integer> stats = new EnumMap<>(Stat.class);
                for (Stat stat : Stat.values()) {
                    stats.put(stat, buf.readVarInt());
                }
                String backstory = buf.readUtf();
                return new CharacterSheetSyncPacket(stats, backstory);
            }
    );

    /** Convenience constructor: snapshot a CharacterSheet into packet form. */
    public CharacterSheetSyncPacket(CharacterSheet sheet) {
        this(copyStats(sheet), sheet.getBackstory());
    }

    private static Map<Stat, Integer> copyStats(CharacterSheet sheet) {
        Map<Stat, Integer> map = new EnumMap<>(Stat.class);
        for (Stat stat : Stat.values()) {
            map.put(stat, sheet.getStat(stat));
        }
        return map;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
