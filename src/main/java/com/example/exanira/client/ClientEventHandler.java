package com.example.exanira.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Handles client-side game events.
 * Registered on NeoForge.EVENT_BUS from ExaniraModClient (client-only).
 */
@OnlyIn(Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (KeyBindings.OPEN_CHARACTER_SHEET.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new CharacterSheetScreen());
            }
        }
    }
}
