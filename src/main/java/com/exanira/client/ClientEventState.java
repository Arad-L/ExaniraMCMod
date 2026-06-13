package com.exanira.client;

import com.exanira.network.EventStartPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Client-side mirror of the currently active event.
 * Populated by EventStartPacket; cleared by EventEndPacket.
 * Read by RadioItem (to decide whether to open EventScreen) and by EventScreen itself.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientEventState {

    private static String activeInstanceKey = null;
    private static List<String> activeDialogue = List.of();
    private static List<EventStartPacket.ChoiceData> choices = List.of();

    private ClientEventState() {}

    public static boolean isActive() {
        return activeInstanceKey != null;
    }

    public static String getInstanceKey() {
        return activeInstanceKey;
    }

    public static List<String> getDialogue() {
        return activeDialogue;
    }

    public static List<EventStartPacket.ChoiceData> getChoices() {
        return choices;
    }

    public static void startEvent(String instanceKey, List<String> dialogue, List<EventStartPacket.ChoiceData> c) {
        activeInstanceKey = instanceKey;
        activeDialogue = List.copyOf(dialogue);
        choices = List.copyOf(c);
    }

    public static void endEvent(String instanceKey) {
        if (instanceKey.equals(activeInstanceKey)) {
            activeInstanceKey = null;
            activeDialogue = List.of();
            choices = List.of();
        }
    }

    /** Unconditionally wipes all state. Called on client disconnect (world exit / server leave). */
    public static void clear() {
        activeInstanceKey = null;
        activeDialogue = List.of();
        choices = List.of();
    }
}

