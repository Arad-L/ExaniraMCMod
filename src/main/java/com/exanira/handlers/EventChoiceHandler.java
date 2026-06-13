package com.exanira.handlers;

import com.exanira.event.EventQueueManager;
import com.exanira.network.EventChoicePacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side handler for EventChoicePacket.
 * All validation (player in event, choice bounds, stat gate) is delegated to EventQueueManager.
 */
public final class EventChoiceHandler {

    private EventChoiceHandler() {}

    public static void handle(EventChoicePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            EventQueueManager.INSTANCE.resolveChoice(
                    player.getUUID(),
                    packet.instanceKey(),
                    packet.choiceIndex(),
                    player
            );
        });
    }
}
