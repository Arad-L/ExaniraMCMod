package com.example.exanira.event;

import com.example.exanira.ExaniraMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads event definitions from {@code data/exanira/events/*.json}.
 * Registered as a server-side reload listener so events refresh on {@code /reload}.
 */
public class EventLoader extends SimplePreparableReloadListener<Map<String, EventDefinition>> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final String EVENTS_SUBPATH = "events";

    @Override
    protected Map<String, EventDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<String, EventDefinition> events = new HashMap<>();

        manager.listResources(EVENTS_SUBPATH,
                loc -> loc.getNamespace().equals(ExaniraMod.MODID) && loc.getPath().endsWith(".json")
        ).forEach((location, resource) -> {
            try (Reader reader = resource.openAsReader()) {
                EventDto dto = GSON.fromJson(reader, EventDto.class);
                if (dto == null || dto.id == null) {
                    LOGGER.warn("[Exanira] Skipping malformed event file: {}", location);
                    return;
                }
                events.put(dto.id, toDefinition(dto));
            } catch (Exception e) {
                LOGGER.error("[Exanira] Failed to load event from {}: {}", location, e.getMessage());
            }
        });

        LOGGER.info("[Exanira] Loaded {} event definition(s)", events.size());
        return events;
    }

    @Override
    protected void apply(Map<String, EventDefinition> data, ResourceManager manager, ProfilerFiller profiler) {
        EventQueueManager.INSTANCE.loadEvents(data);
    }

    private static EventDefinition toDefinition(EventDto dto) {
        EventType type;
        try {
            type = EventType.valueOf(dto.type != null ? dto.type.toUpperCase() : "SIDE");
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[Exanira] Unknown event type '{}' in event '{}', defaulting to SIDE", dto.type, dto.id);
            type = EventType.SIDE;
        }

        Map<String, EventScene> scenes = new HashMap<>();
        if (dto.scenes != null) {
            dto.scenes.forEach((sceneId, sceneDto) -> {
                if (sceneDto == null) return;
                List<EventChoice> choices = sceneDto.choices == null ? List.of() :
                        sceneDto.choices.stream()
                                .filter(c -> c != null && c.text != null)
                                .map(c -> new EventChoice(
                                        c.text,
                                        c.requires != null ? Map.copyOf(c.requires) : Map.of(),
                                        c.checkType != null ? c.checkType : "hard",
                                        c.nextScene,
                                        c.successEvent,
                                        c.lockedText,
                                        c.outcome
                                ))
                                .toList();
                scenes.put(sceneId, new EventScene(
                        sceneId,
                        sceneDto.dialogue != null ? List.copyOf(sceneDto.dialogue) : List.of(),
                        choices,
                        sceneDto.successEvent
                ));
            });
        }

        String startScene = dto.startScene;
        if (startScene == null && !scenes.isEmpty()) {
            startScene = scenes.keySet().iterator().next();
            LOGGER.warn("[Exanira] Event '{}' has no startScene — defaulting to '{}'", dto.id, startScene);
        }

        return new EventDefinition(
                dto.id,
                type,
                dto.npc,
                dto.offlineFallback != null ? dto.offlineFallback : "ignore",
                startScene,
                Map.copyOf(scenes)
        );
    }

    // ── Gson DTOs ──────────────────────────────────────────────────────────────

    private static class EventDto {
        String id;
        String type;
        String npc;
        String offlineFallback;
        String startScene;
        Map<String, SceneDto> scenes;
    }

    private static class SceneDto {
        List<String> dialogue;
        List<ChoiceDto> choices;
        String successEvent;
    }

    private static class ChoiceDto {
        String text;
        Map<String, Integer> requires;
        String checkType;
        String nextScene;
        String successEvent;
        String lockedText;
        String outcome;
    }
}
