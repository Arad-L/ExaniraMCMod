package com.exanira;

import com.exanira.character.CharacterAttachment;
import com.exanira.command.ExaniraCommands;
import com.exanira.event.EventLoader;
import com.exanira.event.EventQueueManager;
import com.exanira.handlers.CharacterCreationHandler;
import com.exanira.handlers.EventChoiceHandler;
import com.exanira.handlers.PlayerLoginHandler;
import com.exanira.item.ExaniraItems;
import com.exanira.network.CharacterCreationSubmitPacket;
import com.exanira.network.EventChoicePacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(ExaniraMod.MODID)
public class ExaniraMod {

    public static final String MODID = "exanira";

    public ExaniraMod(IEventBus modEventBus) {
        CharacterAttachment.ATTACHMENT_TYPES.register(modEventBus);
        ExaniraItems.ITEMS.register(modEventBus);
        NeoForge.EVENT_BUS.register(PlayerLoginHandler.class);
        modEventBus.addListener(ExaniraMod::onRegisterPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(ExaniraMod::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) ->
                ExaniraCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) ->
                EventQueueManager.INSTANCE.clear());

        // ExaniraModClient references Minecraft — only instantiate on physical clients
        if (FMLEnvironment.dist == Dist.CLIENT) {
            new ExaniraModClient(modEventBus);
        }
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(
                CharacterCreationSubmitPacket.TYPE,
                CharacterCreationSubmitPacket.STREAM_CODEC,
                CharacterCreationHandler::handle
        );
        registrar.playToServer(
                EventChoicePacket.TYPE,
                EventChoicePacket.STREAM_CODEC,
                EventChoiceHandler::handle
        );
    }

    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new EventLoader());
    }
}
