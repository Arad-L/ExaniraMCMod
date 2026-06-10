package com.example.exanira.event;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;

/**
 * Stores an in-progress event on the player's own NBT (via AttachmentType).
 *
 * Because this is an attachment with a serializer, it is written to the player's
 * save file (saves/WorldName/playerdata/UUID.dat) synchronously alongside the
 * player's inventory and stats — no async IO, no temp-file race conditions.
 *
 * The data is per-world: each singleplayer world has its own playerdata folder,
 * so switching worlds always gives a fresh attachment for a new player.
 */
public class PendingEventAttachment implements INBTSerializable<CompoundTag> {

    @Nullable private String eventId    = null;
    @Nullable private String sceneId    = null;

    public PendingEventAttachment() {}

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean hasPendingEvent() {
        return eventId != null;
    }

    @Nullable public String getEventId()  { return eventId;  }
    @Nullable public String getSceneId()  { return sceneId;  }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void set(String eventId, String sceneId) {
        this.eventId = eventId;
        this.sceneId = sceneId;
    }

    public void clear() {
        this.eventId = null;
        this.sceneId = null;
    }

    // ── INBTSerializable ─────────────────────────────────────────────────────

    @Override
    public CompoundTag serializeNBT(net.minecraft.core.HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (eventId != null) {
            tag.putString("eventId", eventId);
            tag.putString("sceneId", sceneId != null ? sceneId : "");
        }
        return tag;
    }

    @Override
    public void deserializeNBT(net.minecraft.core.HolderLookup.Provider provider, CompoundTag tag) {
        if (tag.contains("eventId")) {
            this.eventId = tag.getString("eventId");
            this.sceneId = tag.getString("sceneId");
            if (this.sceneId.isEmpty()) this.sceneId = null;
        } else {
            this.eventId = null;
            this.sceneId = null;
        }
    }
}
