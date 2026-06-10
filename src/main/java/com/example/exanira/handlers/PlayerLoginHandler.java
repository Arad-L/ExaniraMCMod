package com.example.exanira.handlers;

import com.example.exanira.character.CharacterAttachment;
import com.example.exanira.character.CharacterSheet;
import com.example.exanira.event.EventQueueManager;
import com.example.exanira.item.ExaniraItems;
import com.example.exanira.network.CharacterSheetSyncPacket;
import com.example.exanira.network.OpenCharacterCreationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Handles server-side player lifecycle events.
 * Registered on NeoForge.EVENT_BUS from ExaniraMod constructor.
 */
public class PlayerLoginHandler {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        CharacterSheet sheet = player.getData(CharacterAttachment.CHARACTER_SHEET.get());

        if (!sheet.isInitialized()) {
            PacketDistributor.sendToPlayer(player, new OpenCharacterCreationPacket());
        } else {
            ExaniraItems.ensureRadio(player);
            EventQueueManager.INSTANCE.resyncPlayerIfMidEvent(player);
            PacketDistributor.sendToPlayer(player, new CharacterSheetSyncPacket(sheet));
        }
    }
}
