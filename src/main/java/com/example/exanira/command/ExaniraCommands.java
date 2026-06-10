package com.example.exanira.command;

import com.example.exanira.event.EventDefinition;
import com.example.exanira.event.EventQueueManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Registers the {@code /exanira} command tree.
 * Requires permission level 2 (op).
 *
 * Usage: /exanira event start <eventId>
 */
public class ExaniraCommands {

    private ExaniraCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("exanira")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("event")
                                .then(Commands.literal("start")
                                        .then(Commands.argument("eventId", StringArgumentType.string())
                                                .executes(ExaniraCommands::executeEventStart)))
                                .then(Commands.literal("stop")
                                        // /exanira event stop — stops the command source's own event
                                        .executes(ExaniraCommands::executeEventStopSelf)
                                        // /exanira event stop <player> — stops another player's event
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ExaniraCommands::executeEventStopTarget))))
        );
    }

    private static int executeEventStart(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "eventId");
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Optional<EventDefinition> def = EventQueueManager.INSTANCE.getDefinition(id);
        if (def.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Unknown event id: '" + id + "'"));
            return 0;
        }

        boolean started = EventQueueManager.INSTANCE.startEvent(id, player);
        if (!started) {
            ctx.getSource().sendFailure(
                    Component.literal("Could not start event — player may already be in an event."));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("Started event '" + id + "'."), false);
        return 1;
    }

    private static int executeEventStopSelf(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean stopped = EventQueueManager.INSTANCE.forceStopEvent(player);
        if (!stopped) {
            ctx.getSource().sendFailure(Component.literal("You are not currently in any event."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Event stopped."), false);
        return 1;
    }

    private static int executeEventStopTarget(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        boolean stopped = EventQueueManager.INSTANCE.forceStopEvent(target);
        if (!stopped) {
            ctx.getSource().sendFailure(
                    Component.literal(target.getName().getString() + " is not currently in any event."));
            return 0;
        }
        ctx.getSource().sendSuccess(
                () -> Component.literal("Event stopped for " + target.getName().getString() + "."), true);
        return 1;
    }
}
