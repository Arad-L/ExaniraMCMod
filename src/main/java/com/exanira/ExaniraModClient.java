package com.exanira;

import com.exanira.client.CharacterCreationScreen;
import com.exanira.client.ClientCharacterData;
import com.exanira.client.ClientEventHandler;
import com.exanira.client.ClientEventState;
import com.exanira.client.EventScreen;
import com.exanira.client.KeyBindings;
import com.exanira.network.CharacterSheetSyncPacket;
import com.exanira.network.EventEndPacket;
import com.exanira.network.EventStartPacket;
import com.exanira.network.OpenCharacterCreationPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Client-only initialisation. Instantiated from ExaniraMod only when running on a
 * physical client (FMLEnvironment.dist == Dist.CLIENT). Never loaded on dedicated servers.
 */
@OnlyIn(Dist.CLIENT)
public class ExaniraModClient {

    public ExaniraModClient(IEventBus modEventBus) {
        // Game-bus events (client tick, keybind)
        NeoForge.EVENT_BUS.register(ClientEventHandler.class);
        // Clear client event state whenever the local player disconnects from any world/server
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientEventState.clear());

        // Mod-bus events
        modEventBus.addListener(this::onRegisterKeyMappings);
        modEventBus.addListener(this::onRegisterPayloadHandlers);
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_CHARACTER_SHEET);
    }

    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");

        // Server → Client: open the creation screen on first login
        registrar.playToClient(
                OpenCharacterCreationPacket.TYPE,
                OpenCharacterCreationPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() ->
                        Minecraft.getInstance().setScreen(new CharacterCreationScreen()))
        );

        // Server → Client: sync full character sheet after creation or on reconnect
        registrar.playToClient(
                CharacterSheetSyncPacket.TYPE,
                CharacterSheetSyncPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() ->
                        ClientCharacterData.update(packet.stats(), packet.backstory()))
        );

        // Server → Client: open event screen OR advance to next scene
        registrar.playToClient(
                EventStartPacket.TYPE,
                EventStartPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> {
                    ClientEventState.startEvent(packet.instanceKey(), packet.dialogue(), packet.choices());
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.screen instanceof EventScreen eventScreen) {
                        eventScreen.refresh(); // rebuild buttons for the new scene in-place
                    }
                })
        );

        // Server → Client: clear event state; also close EventScreen if it's open
        registrar.playToClient(
                EventEndPacket.TYPE,
                EventEndPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> {
                    ClientEventState.endEvent(packet.instanceKey());
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.screen instanceof EventScreen) mc.setScreen(null);
                })
        );
    }
}
