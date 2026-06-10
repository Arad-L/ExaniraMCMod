package com.example.exanira.event;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A running instance of an event.
 * Tracks which players are participating, the current scene, and whether the event has resolved.
 */
public class ActiveEvent {

    private final EventDefinition definition;
    private final Set<UUID> participants;
    private String currentSceneId;
    private boolean resolved = false;

    public ActiveEvent(EventDefinition definition, UUID startingPlayer) {
        this.definition = definition;
        this.participants = new HashSet<>();
        this.participants.add(startingPlayer);
        this.currentSceneId = definition.startScene();
    }

    public EventDefinition definition() {
        return definition;
    }

    public Set<UUID> participants() {
        return Collections.unmodifiableSet(participants);
    }

    public EventScene currentScene() {
        return definition.scenes().get(currentSceneId);
    }

    public void setCurrentScene(String sceneId) {
        this.currentSceneId = sceneId;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void markResolved() {
        this.resolved = true;
    }
}
