package com.example.exanira.client;

import com.example.exanira.character.Stat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.EnumMap;
import java.util.Map;

/**
 * Client-side mirror of the player's CharacterSheet.
 * Populated by CharacterSheetSyncPacket on login and on any stat change.
 * Only access from client-side code.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientCharacterData {

    private static Map<Stat, Integer> stats = defaultStats();
    private static String backstory = "";

    private ClientCharacterData() {}

    private static Map<Stat, Integer> defaultStats() {
        Map<Stat, Integer> map = new EnumMap<>(Stat.class);
        for (Stat stat : Stat.values()) {
            map.put(stat, 0);
        }
        return map;
    }

    public static void update(Map<Stat, Integer> newStats, String newBackstory) {
        stats = new EnumMap<>(newStats);
        backstory = newBackstory;
    }

    public static int getStat(Stat stat) {
        return stats.getOrDefault(stat, 0);
    }

    public static String getBackstory() {
        return backstory;
    }
}
