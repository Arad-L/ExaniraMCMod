package com.exanira.event;

import com.exanira.character.CharacterAttachment;
import com.exanira.character.CharacterSheet;
import com.exanira.character.Stat;
import com.exanira.item.ExaniraItems;
import com.exanira.network.EventEndPacket;
import com.exanira.network.EventStartPacket;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central manager for all active events.
 * MUST be the sole entry point for spawning, tracking, and resolving events.
 * Per-player scattered logic causes multiplayer desync — never bypass this class.
 */
public class EventQueueManager {

    public static final EventQueueManager INSTANCE = new EventQueueManager();
    private static final Logger LOGGER = LogUtils.getLogger();

    /** instanceKey (eventId#playerUUID) → running event */
    private final Map<String, ActiveEvent> activeEvents = new ConcurrentHashMap<>();
    /** playerUUID → instanceKey they are currently in */
    private final Map<UUID, String> playerToEvent = new ConcurrentHashMap<>();
    /** All loaded event definitions; refreshed on /reload */
    private Map<String, EventDefinition> loadedEvents = Map.of();

    private EventQueueManager() {}

    public void loadEvents(Map<String, EventDefinition> events) {
        this.loadedEvents = Map.copyOf(events);
    }

    public Optional<EventDefinition> getDefinition(String id) {
        return Optional.ofNullable(loadedEvents.get(id));
    }

    /**
     * Starts an event for a player.
     * Sends dialogue to chat then opens the choice HUD client-side.
     *
     * @return false if the event id is unknown or the player is already in an event.
     */
    public boolean startEvent(String eventId, ServerPlayer player) {
        EventDefinition def = loadedEvents.get(eventId);
        if (def == null) {
            LOGGER.warn("[Exanira] Unknown event id: '{}'", eventId);
            return false;
        }

        if (playerToEvent.containsKey(player.getUUID())) {
            LOGGER.debug("[Exanira] Player {} is already in event '{}'",
                    player.getName().getString(), playerToEvent.get(player.getUUID()));
            player.sendSystemMessage(Component.literal(
                    "You're already dealing with something. Finish that first.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        String instanceKey = eventId + "#" + player.getUUID();
        ActiveEvent active = new ActiveEvent(def, player.getUUID());

        // Validate start scene before committing
        EventScene startScene = def.scenes().get(def.startScene());
        if (startScene == null) {
            LOGGER.error("[Exanira] Event '{}' startScene '{}' not found in scenes map",
                    eventId, def.startScene());
            return false;
        }

        activeEvents.put(instanceKey, active);
        playerToEvent.put(player.getUUID(), instanceKey);

        // Persist to the player attachment (saved synchronously with the player's NBT per-world)
        PendingEventAttachment pending = player.getData(CharacterAttachment.PENDING_EVENT.get());
        pending.set(eventId, def.startScene());
        LOGGER.info("[Exanira] startEvent: attachment set player={} event={} scene={}",
                player.getUUID(), eventId, def.startScene());

        player.sendSystemMessage(Component.literal("─────────────────────────").withStyle(ChatFormatting.DARK_GRAY));
        for (String line : startScene.dialogue()) {
            player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.AQUA));
        }
        player.sendSystemMessage(Component.literal("[ Right-click your Radio to respond ]")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));

        // Build choice availability using server-side stat check
        CharacterSheet sheet = player.getData(CharacterAttachment.CHARACTER_SHEET.get());
        List<EventStartPacket.ChoiceData> choiceData = buildChoiceData(startScene.choices(), sheet);

        // Light up the radio in the player's inventory
        setRadioActive(player, true);

        PacketDistributor.sendToPlayer(player, new EventStartPacket(instanceKey, startScene.dialogue(), choiceData));
        return true;
    }

    /**
     * Resolves a player's choice. Re-validates the stat gate server-side to guard
     * against client-side manipulation.
     */
    public void resolveChoice(UUID playerId, String instanceKey, int choiceIndex, ServerPlayer player) {
        ActiveEvent active = activeEvents.get(instanceKey);
        if (active == null || active.isResolved()) return;
        if (!active.participants().contains(playerId)) return;

        EventDefinition def = active.definition();
        EventScene scene = active.currentScene();
        if (scene == null) return;

        // choiceIndex == -1 means the player dismissed a terminal scene (no choices)
        if (choiceIndex == -1) {
            if (!scene.choices().isEmpty()) return; // only valid for terminal scenes
            active.markResolved();
            endEvent(instanceKey, playerId, player, scene.successEvent());
            return;
        }

        if (choiceIndex < 0 || choiceIndex >= scene.choices().size()) return;

        EventChoice choice = scene.choices().get(choiceIndex);

        // Server-side hard gate re-check
        CharacterSheet sheet = player.getData(CharacterAttachment.CHARACTER_SHEET.get());
        if (!meetsRequirements(choice, sheet)) {
            LOGGER.warn("[Exanira] Player {} attempted a locked choice ({}) in event '{}'",
                    player.getName().getString(), choiceIndex, def.id());
            return;
        }

        // Feedback in chat
        player.sendSystemMessage(Component.literal("You chose: " + choice.text()).withStyle(ChatFormatting.YELLOW));
        String outcome = choice.outcome() != null ? choice.outcome() : "none";
        if ("nothing_happens".equals(outcome)) {
            player.sendSystemMessage(Component.literal("Nothing happens.").withStyle(ChatFormatting.GRAY));
        }
        player.sendSystemMessage(Component.literal("─────────────────────────").withStyle(ChatFormatting.DARK_GRAY));
        LOGGER.info("[Exanira] {} chose '{}' in '{}:{}' → outcome: {}",
                player.getName().getString(), choice.text(), def.id(), scene.id(), outcome);

        if (choice.nextScene() != null) {
            // ── Advance to next scene within this event ──────────────────────
            EventScene next = def.scenes().get(choice.nextScene());
            if (next == null) {
                LOGGER.error("[Exanira] nextScene '{}' not found in event '{}' — ending event",
                        choice.nextScene(), def.id());
                endEvent(instanceKey, playerId, player, null);
                return;
            }
            active.setCurrentScene(choice.nextScene());
            // Don't mark resolved — event continues

            // Update the persisted scene in the player attachment
            PendingEventAttachment pendingAdv = player.getData(CharacterAttachment.PENDING_EVENT.get());
            pendingAdv.set(def.id(), choice.nextScene());
            LOGGER.info("[Exanira] scene advance: attachment updated player={} scene={}",
                    player.getUUID(), choice.nextScene());
            player.sendSystemMessage(Component.literal("─────────────────────────").withStyle(ChatFormatting.DARK_GRAY));
            for (String line : next.dialogue()) {
                player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.AQUA));
            }
            if (!next.choices().isEmpty()) {
                player.sendSystemMessage(Component.literal("[ Right-click your Radio to respond ]")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
            }

            // Push updated scene to client (EventScreen rebuilds in-place)
            CharacterSheet nextSheet = player.getData(CharacterAttachment.CHARACTER_SHEET.get());
            List<EventStartPacket.ChoiceData> nextChoiceData = buildChoiceData(next.choices(), nextSheet);
            PacketDistributor.sendToPlayer(player, new EventStartPacket(instanceKey, next.dialogue(), nextChoiceData));

        } else {
            // ── End the event (no nextScene) ─────────────────────────────────
            active.markResolved();
            endEvent(instanceKey, playerId, player, choice.successEvent());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Cleans up an event and optionally chains to another event. */
    private void endEvent(String instanceKey, UUID playerId, ServerPlayer player, String successEventId) {
        activeEvents.remove(instanceKey);
        playerToEvent.remove(playerId);

        // Clear the player attachment — written synchronously with player save, no async IO race
        PendingEventAttachment pending = player.getData(CharacterAttachment.PENDING_EVENT.get());
        pending.clear();
        LOGGER.info("[Exanira] endEvent: attachment cleared player={} instanceKey={}", playerId, instanceKey);

        PacketDistributor.sendToPlayer(player, new EventEndPacket(instanceKey));

        if (successEventId != null) {
            boolean chained = startEvent(successEventId, player);
            if (!chained) {
                LOGGER.warn("[Exanira] Failed to chain to '{}' — event id unknown", successEventId);
                setRadioActive(player, false);
            }
        } else {
            setRadioActive(player, false);
        }
    }

    private List<EventStartPacket.ChoiceData> buildChoiceData(List<EventChoice> choices, CharacterSheet sheet) {
        return choices.stream()
                .map(choice -> new EventStartPacket.ChoiceData(
                        choice.text(),
                        meetsRequirements(choice, sheet),
                        choice.lockedText() != null ? choice.lockedText() : "Stat requirement not met.",
                        buildRequirementText(choice)
                ))
                .toList();
    }

    /** Formats requirement map to e.g. "[PER 3+]" — shown on all choices, met or not. */
    private String buildRequirementText(EventChoice choice) {
        if (choice.requires() == null || choice.requires().isEmpty()) return "";
        return choice.requires().entrySet().stream()
                .map(e -> "[" + e.getKey().substring(0, Math.min(3, e.getKey().length())).toUpperCase()
                        + " " + e.getValue() + "+]")
                .collect(Collectors.joining(" "));
    }

    private boolean meetsRequirements(EventChoice choice, CharacterSheet sheet) {
        if (choice.requires() == null || choice.requires().isEmpty()) return true;
        for (Map.Entry<String, Integer> req : choice.requires().entrySet()) {
            try {
                Stat stat = Stat.valueOf(req.getKey().toUpperCase());
                if (sheet.getStat(stat) < req.getValue()) return false;
            } catch (IllegalArgumentException e) {
                LOGGER.warn("[Exanira] Unknown stat name '{}' in event requirement", req.getKey());
            }
        }
        return true;
    }

    /** Sets or clears the glowing "active" flag on the player's Radio item (if they have one). */
    private void setRadioActive(ServerPlayer player, boolean active) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ExaniraItems.RADIO.get())) {
                CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                tag.putBoolean("active", active);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                break;
            }
        }
    }

    /** Returns true if the player is currently assigned to an active event. */
    public boolean isPlayerInEvent(UUID playerId) {
        return playerToEvent.containsKey(playerId);
    }

    /**
     * Clears all in-memory event state. Called on server stop to prevent stale data
     * from leaking into a new world session (the JVM and its static singletons survive
     * between singleplayer world loads).
     */
    public void clear() {
        activeEvents.clear();
        playerToEvent.clear();
        LOGGER.info("[Exanira] EventQueueManager cleared on server stop.");
    }

    /**
     * Debug command: forcibly removes the player from their current event,
     * sends EventEndPacket, and turns off the radio glow.
     *
     * @return false if the player was not in any event.
     */
    public boolean forceStopEvent(ServerPlayer player) {
        String instanceKey = playerToEvent.remove(player.getUUID());
        if (instanceKey == null) return false;

        ActiveEvent active = activeEvents.remove(instanceKey);
        if (active != null) active.markResolved();

        player.getData(CharacterAttachment.PENDING_EVENT.get()).clear();
        setRadioActive(player, false);
        PacketDistributor.sendToPlayer(player, new EventEndPacket(instanceKey));
        LOGGER.info("[Exanira] Force-stopped event '{}' for {}", instanceKey, player.getName().getString());
        return true;
    }

    /**
     * Called on player login. If this player was mid-event when they disconnected
     * (event still in memory), re-sends the EventStartPacket so the client can
     * reopen the EventScreen by right-clicking the radio.
     */
    public void resyncPlayerIfMidEvent(ServerPlayer player) {
        String instanceKey = playerToEvent.get(player.getUUID());
        if (instanceKey != null) {
            // In-memory maps are warm (e.g. quick reconnect without server restart)
            ActiveEvent active = activeEvents.get(instanceKey);
            if (active != null && !active.isResolved()) {
                CharacterSheet sheet = player.getData(CharacterAttachment.CHARACTER_SHEET.get());
                EventScene currentScene = active.currentScene();
                if (currentScene != null) {
                    List<EventStartPacket.ChoiceData> choiceData = buildChoiceData(currentScene.choices(), sheet);
                    PacketDistributor.sendToPlayer(player,
                            new EventStartPacket(instanceKey, currentScene.dialogue(), choiceData));
                    LOGGER.info("[Exanira] Re-synced mid-event '{}' to reconnecting player {}",
                            active.definition().id(), player.getName().getString());
                }
                return;
            }
            // Stale entry — clean up
            playerToEvent.remove(player.getUUID());
        }

        // In-memory maps are empty (server was restarted / singleplayer world was reloaded).
        // Read the player attachment — loaded fresh from disk for this world.
        PendingEventAttachment pending = player.getData(CharacterAttachment.PENDING_EVENT.get());
        LOGGER.info("[Exanira] resync: attachment for {} hasPending={} event={} scene={}",
                player.getName().getString(), pending.hasPendingEvent(), pending.getEventId(), pending.getSceneId());

        if (!pending.hasPendingEvent()) {
            // No active event — clear any stale radio glow
            setRadioActive(player, false);
            return;
        }

        // Try to resolve the event definition
        EventDefinition def = loadedEvents.get(pending.getEventId());
        if (def == null) {
            LOGGER.warn("[Exanira] Attachment references unknown event '{}' for player {} — clearing",
                    pending.getEventId(), player.getName().getString());
            pending.clear();
            setRadioActive(player, false);
            return;
        }

        // Resolve the scene; fall back to startScene if the saved one no longer exists
        String sceneId = pending.getSceneId() != null ? pending.getSceneId() : def.startScene();
        EventScene scene = def.scenes().get(sceneId);
        if (scene == null) {
            LOGGER.warn("[Exanira] Saved scene '{}' not found in event '{}' — restarting from beginning",
                    sceneId, def.id());
            sceneId = def.startScene();
            scene = def.scenes().get(sceneId);
        }
        if (scene == null) {
            LOGGER.error("[Exanira] startScene '{}' missing from event '{}' — clearing",
                    def.startScene(), def.id());
            pending.clear();
            setRadioActive(player, false);
            return;
        }

        // Reconstruct the ActiveEvent in memory
        instanceKey = def.id() + "#" + player.getUUID();
        ActiveEvent active = new ActiveEvent(def, player.getUUID());
        active.setCurrentScene(sceneId);
        activeEvents.put(instanceKey, active);
        playerToEvent.put(player.getUUID(), instanceKey);

        // Resync scene on client
        CharacterSheet sheet = player.getData(CharacterAttachment.CHARACTER_SHEET.get());
        List<EventStartPacket.ChoiceData> choiceData = buildChoiceData(scene.choices(), sheet);
        setRadioActive(player, true);
        PacketDistributor.sendToPlayer(player, new EventStartPacket(instanceKey, scene.dialogue(), choiceData));
        LOGGER.info("[Exanira] Reconstructed event '{}' (scene: '{}') for reconnecting player {}",
                def.id(), sceneId, player.getName().getString());
    }
}
