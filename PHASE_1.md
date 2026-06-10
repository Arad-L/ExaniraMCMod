# Phase 1 Execution — Foundation

**Goal**: Persistent character sheet with profession-based stats, lifestyle-driven backstory, multi-step creation screen on first login, and a display GUI.
No event logic. No multiplayer hooks. Foundation only.

**Status**: 🟢 Complete — verified in-game 2026-06-07

| # | Task | Status | Notes |
|---|------|--------|-------|
| 1.1 | Set up mod package structure | 🟢 Complete | `character/`, `backstory/`, `events/`, `network/`, `client/` |
| 1.2 | Register `CharacterSheet` Data Attachment | 🟢 Complete | `CharacterAttachment.java` using `AttachmentType.serializable()` |
| 1.3 | Implement core stat model | 🟢 Complete | `Stat.java`, `Profession.java` presets, `CharacterSheet.java`; stat range 1–10 |
| 1.4 | Character creation data definitions | 🟢 Complete | `LifestyleOption`, `LifestyleQuestion`, `CharacterCreationDefs` (5 questions, 3 opts each) |
| 1.5 | Selection-based backstory generator | 🟢 Complete | `BackstoryGenerator` uses profession + Q1/Q3 flavor; random flavor pool per creation |
| 1.6 | Multi-step character creation screen | 🟢 Complete | `CharacterCreationScreen.java` — Escape-blocked, 8 professions + 5 questions + confirm step |
| 1.7 | Creation packets + server handler | 🟢 Complete | `OpenCharacterCreationPacket`, `CharacterCreationSubmitPacket`, `CharacterCreationHandler` |
| 1.8 | Character sheet display GUI + keybind | 🟢 Complete | `CharacterSheetScreen.java`, `C` key, registered in `ExaniraModClient` |
| 1.9 | End-to-end test (creation + display) | 🟢 Complete | Creation flow, stats, and backstory verified in-game |

---

## 1.1 — Package Structure

Create the following package layout under `com.example.examplemod`:

```
character/
    CharacterSheet.java         — the data model (stats + backstory string)
    CharacterStats.java         — enum or constants for the 6 stats
    CharacterAttachment.java    — NeoForge AttachmentType registration

backstory/
    BackstoryGenerator.java     — MADLIBS template engine
    BackstoryTemplate.java      — template data holder
    BackstoryPools.java         — static input lists (professions, traits, etc.)

gui/
    CharacterSheetScreen.java   — the Minecraft Screen subclass
    CharacterSheetScreenHandler.java  — optional, only if using container-based screen
```

**Status**: � Complete

```
character/
    CharacterSheet.java         ✅
    Stat.java                   ✅
    CharacterAttachment.java    ✅

backstory/
    BackstoryGenerator.java     ✅
    BackstoryTemplate.java      ✅
    BackstoryPools.java         ✅

events/
    PlayerLoginHandler.java     ✅

network/
    CharacterSheetSyncPacket.java ✅

client/
    ClientCharacterData.java    ✅
    CharacterSheetScreen.java   ✅
    KeyBindings.java            ✅
    ClientEventHandler.java     ✅
``` — Register CharacterSheet Data Attachment

NeoForge 1.21.1 uses `AttachmentType<T>` instead of the old Capability system.

**API pattern**:
```java
// CharacterAttachment.java
public class CharacterAttachment {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ExampleMod.MODID);

    public static final Supplier<AttachmentType<CharacterSheet>> CHARACTER_SHEET =
        ATTACHMENT_TYPES.register("character_sheet", () ->
            AttachmentType.serializable(CharacterSheet::new).build()
        );
}
```

- `CharacterSheet` must implement `INBTSerializable<CompoundTag>` so it survives world save/load
- Register `ATTACHMENT_TYPES` deferred register on the mod event bus in `ExampleMod` constructor

**Decisions needed before implementing**:
- [ ] Confirm `CharacterSheet` will be attached to `Player` (not `ServerPlayer` specifically) so client-side reads work

**Status**: � Complete

> **Implementation note**: Used `AttachmentType.serializable(CharacterSheet::new).build()`. `CharacterSheet` implements `INBTSerializable<CompoundTag>` with `HolderLookup.Provider` on both methods (required in 1.21.1). Registered via `CharacterAttachment.ATTACHMENT_TYPES.register(modEventBus)` in `ExampleMod` constructor.

---

## 1.3 — Core Stat Model

Six stats, each stored as a simple `int`. Base values set at character creation. Can be upgraded later (Phase 4+).

```java
// CharacterStats.java
public enum Stat {
    STRENGTH,
    AGILITY,
    INTELLIGENCE,
    PERCEPTION,
    LEADERSHIP,
    SURVIVAL
}
```

```java
// CharacterSheet.java — relevant fields
private final Map<Stat, Integer> stats = new EnumMap<>(Stat.class);
```

Default base value for all stats: **1** (so no stat starts at 0/locked).  
**Status**: 🟢 Complete

> **Stat value range**: Still TBD (Q1 below). All stats initialize to 1. No maximum enforced yet — decide before writing Phase 2 JSON events.

---

## 1.4 — MADLIBS Backstory Generator

Pure template engine. No AI. Runs once at character creation and stores the result as a `String` in `CharacterSheet`.

**Template format**:
```
"You were a {profession} in {location}. Before the outbreak, you were known for {trait}.
When everything fell apart, you lost {something} and now you trust {thing} the least."
```

**Input pools** (minimum viable — expand later):

| Pool key | Example values |
|---|---|
| `profession` | soldier, nurse, mechanic, teacher, scavenger, radio operator |
| `location` | a coastal city, a small farm town, a military base, the suburbs |
| `trait` | your calm under pressure, your sharp memory, your distrust of authority |
| `something` | your family, your crew, your sense of purpose, your home |
| `thing` | strangers, authority, luck, technology |

Pools live in `BackstoryPools.java` as `static final List<String>` fields. Selection is random using `RandomSource` (Minecraft's random, seeded per-player UUID for reproducibility if desired).

**Status**: 🟢 Complete

> **Implementation note**: 5 pools (profession, location, trait, loss, distrust). Template resolved via simple string replacement. Stored as `String backstory` inside `CharacterSheet` NBT.

---

## 1.5 — Initialize Attachment on First Login

Hook `PlayerEvent.PlayerLoggedInEvent` (NeoForge event, fires server-side on login).

```java
// In a server-side event handler class
@SubscribeEvent
public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
    Player player = event.getEntity();
    CharacterSheet sheet = player.getData(CharacterAttachment.CHARACTER_SHEET);
    if (!sheet.isInitialized()) {
        sheet.initialize(player.getRandom()); // sets default stats + generates backstory
    }
}
```

**Status**: 🟢 Complete

> **Implementation note**: Registered via `NeoForge.EVENT_BUS.register(PlayerLoginHandler.class)` in `ExampleMod` constructor (explicit, avoids `@EventBusSubscriber` bus ambiguity). Init is idempotent — skipped if `sheet.isInitialized()` is already true.

---

## 1.6 — Character Sheet GUI Screen

Client-side only (`Dist.CLIENT`). A simple `Screen` subclass — no container, no server round-trip needed since stats will be synced to client.

**Layout (basic)**:
```
┌─────────────────────────────────┐
│        CHARACTER SHEET          │
│                                 │
│  [Player skin icon]             │
│  Strength     : 1               │
│  Agility      : 1               │
│  Intelligence : 1               │
│  Perception   : 1               │
│  Leadership   : 1               │
│  Survival     : 1               │
│                                 │
│  BACKSTORY:                     │
│  "You were a mechanic in a      │
│   small farm town..."           │
│                                 │
│           [ Close ]             │
└─────────────────────────────────┘
```

**Status**: 🟢 Complete

> **Implementation note**: `CharacterSheetSyncPacket` is a common-safe record (no client imports). Packet handler registered in `ExampleModClient` via `RegisterPayloadHandlersEvent` so `ClientCharacterData` is never loaded on a dedicated server. Stats encoded as VarInts in `Stat.values()` ordinal order.

---

## 1.7 — Keybind to Open GUI

Register a client-side keybind (default: `C`) to open the character sheet screen.

```java
// In ExampleModClient.java or a dedicated KeyBindings class
public static final KeyMapping OPEN_CHARACTER_SHEET = new KeyMapping(
    "key.examplemod.character_sheet",
    GLFW.GLFW_KEY_C,
    "key.categories.examplemod"
);
```

**Status**: 🟢 Complete

> Default key: `C`. Registered via `RegisterKeyMappingsEvent` in `ExampleModClient`. Consumed in `ClientEventHandler` on `ClientTickEvent.Post` (registered on `NeoForge.EVENT_BUS` from `ExampleModClient` constructor).

---

## 1.8 — End-to-End Test Checklist

Before Phase 1 is considered done:

- [x] New player logs in → `CharacterSheet` is created and initialized
- [x] Stats all default to 1
- [x] Backstory string is generated and non-empty
- [x] Data persists across server restart (NBT serialization works)
- [x] Press `C` → GUI opens
- [x] GUI displays correct stat values
- [x] GUI displays backstory text
- [ ] Player skin icon renders (not implemented — deferred)
- [x] No errors in server or client logs during login or GUI open

**Status**: 🟢 Complete — all functional items verified in-game

---

## Open Questions for Phase 1

| # | Question | Blocking? |
|---|----------|-----------|
| Q1 | What is the stat value range (min/max)? Affects JSON skill check thresholds in Phase 2. | Not blocking Phase 1, but decide before Phase 2 starts |
| Q2 | Should backstory generation be re-rollable by the player, or locked at first login? | Not blocking |
| Q3 | Does the character sheet GUI need a title/name field, or just stats + backstory? | Not blocking |

---

## Files to Create in Phase 1

| File | Purpose |
|------|---------|
| `character/CharacterSheet.java` | Data model + NBT serialization |
| `character/CharacterStats.java` | Stat enum |
| `character/CharacterAttachment.java` | AttachmentType registration |
| `backstory/BackstoryGenerator.java` | Template resolver |
| `backstory/BackstoryTemplate.java` | Template string + slot keys |
| `backstory/BackstoryPools.java` | Input word/phrase lists |
| `gui/CharacterSheetScreen.java` | GUI screen |
| `network/CharacterSheetSyncPacket.java` | Server→client stat sync |
| `events/PlayerLoginHandler.java` | Login event hook for initialization |
