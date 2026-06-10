# Phase 2 Execution — Event Engine (Minimum)

**Goal**: JSON-driven events that fire via operator command, show dialogue in chat, present choice buttons in a proper GUI with hard stat gates, and give the player a Radio item to open the event screen.  
No automatic triggers. No multiplayer party logic. Single-player event flow only.

**Status**: � Complete — verified in-game 2026-06-07

| # | Task | Status | Notes |
|---|------|--------|-------|
| 2.1 | Event data model | 🟢 Complete | `EventType`, `EventChoice`, `EventDefinition`, `ActiveEvent` in `event/` package |
| 2.2 | JSON event loader | 🟢 Complete | `EventLoader` — `SimplePreparableReloadListener`, reads `data/exanira/events/*.json`, refreshes on `/reload` |
| 2.3 | `EventQueueManager` | 🟢 Complete | Singleton; owns active event map, player→event map, stat gate checks, radio flag logic, reconnect resync |
| 2.4 | Event trigger (command) | 🟢 Complete | `/exanira event start <id>` and `/exanira event stop [<player>]` — op level 2; wired via `RegisterCommandsEvent` on game bus |
| 2.5 | Stat gate hard checks | 🟢 Complete | Server-side re-validation in `resolveChoice()`; requirement shown on button as `[PER 3+]` even when met |
| 2.6 | Network packets | 🟢 Complete | `EventStartPacket` (S→C, includes dialogue + choice availability), `EventChoicePacket` (C→S), `EventEndPacket` (S→C) |
| 2.7 | Server-side choice handler | 🟢 Complete | `EventChoiceHandler` — delegates to `EventQueueManager`; guards against stat spoofing |
| 2.8 | Radio item | 🟢 Complete | `RadioItem` + `ExaniraItems`; given on login via `ensureRadio()`; glows (enchantment foil) when event is active |
| 2.9 | Event screen GUI | 🟢 Complete | `EventScreen` — proper `Screen` subclass, captures mouse, shows dialogue + choice buttons, Escape-closable |
| 2.10 | Client event state | 🟢 Complete | `ClientEventState` — mirrors active event; read by `RadioItem` and `EventScreen` |
| 2.11 | Offline reconnect resync | 🟢 Complete | `resyncPlayerIfMidEvent()` — re-sends `EventStartPacket` on login if player was mid-event |
| 2.12 | Test event JSON | 🟢 Complete | `data/exanira/events/abandoned_radio_station.json` — 3 choices, 2 gated (PER 3+, INT 4+) |
| 2.13 | Radio texture | 🟢 Complete | Add `src/main/resources/assets/exanira/textures/item/radio.png` (16×16); model already points at `exanira:item/radio` |
| 2.14 | End-to-end test | 🟢 Complete | `/exanira event start abandoned_radio_station`, verify glow, open screen, test locked + unlocked choices |

---

## Design Decisions Made in Phase 2

| Decision | Choice | Rationale |
|---|---|---|
| Event trigger | Command-only (`/exanira event start <id>`) | Simplest for testing; automatic triggers deferred to Phase 3+ |
| Choice UI | Proper `Screen` (mouse-capturing) via right-click Radio | HUD overlay discarded — unusable without mouse capture |
| Event chaining (`successEvent`) | Logged only | Deferred to Phase 3 |
| Locked choices | Visible, greyed-out, hover shows `lockedText` | Narrative tension; player knows what they're missing |
| Stat display | `[STAT N+]` prefix shown on all choices, met or not | Players learn what skills matter even when they pass |

---

## Files Created in Phase 2

```
event/
    EventType.java              — MAIN / SIDE / AMBIENT enum
    EventChoice.java            — single choice record (text, requires, lockedText, outcome, successEvent)
    EventDefinition.java        — full parsed event record
    ActiveEvent.java            — running instance (participants, resolved flag)
    EventLoader.java            — reload listener; parses data/exanira/events/*.json
    EventQueueManager.java      — singleton; all event spawn/resolve/cleanup logic

handlers/
    PlayerLoginHandler.java     — NeoForge @SubscribeEvent listener for PlayerLoggedInEvent
    CharacterCreationHandler.java — packet handler for CharacterCreationSubmitPacket
    EventChoiceHandler.java     — packet handler for EventChoicePacket

command/
    ExaniraCommands.java        — /exanira event start <id>

network/
    EventStartPacket.java       — S→C: instanceKey + dialogue + List<ChoiceData>
    EventChoicePacket.java      — C→S: instanceKey + choiceIndex
    EventEndPacket.java         — S→C: instanceKey (signals client to close screen + clear state)

item/
    RadioItem.java              — right-click opens EventScreen; isFoil() = active flag in CUSTOM_DATA
    ExaniraItems.java           — DeferredRegister<Item>; ensureRadio() utility

client/
    ClientEventState.java       — client mirror of active event (instanceKey, dialogue, choices)
    EventScreen.java            — Screen subclass; dialogue display + available/locked choice buttons

src/main/resources/
    data/exanira/events/
        abandoned_radio_station.json    — first test event
    assets/exanira/models/item/
        radio.json                      — item model (exanira:item/radio)
    assets/exanira/textures/item/
        .gitkeep                        — placeholder; replace with radio.png
```

---

## Bug Fixes Applied Post-Phase-2

| Bug | Root Cause | Fix |
|---|---|---|
| Scene advance (nextScene) locks the event | `active.markResolved()` was called unconditionally before the `nextScene` check — every subsequent `resolveChoice()` returned early at the `isResolved()` guard | Moved `markResolved()` to only the terminal path (no `nextScene` + no `-1` dismiss) |
| Event state leaks between singleplayer worlds (attempt 1) | `EventQueueManager.INSTANCE` is a JVM-level static; its maps survive server stop | Added `ServerStoppedEvent` → `clear()` (server) and `ClientPlayerNetworkEvent.LoggingOut` → `ClientEventState.clear()` (client) |
| Event state leaks between singleplayer worlds (attempt 2 — root cause) | `ExaniraEventSavedData` used NeoForge's async atomic write (temp file → rename). On Windows, the rename threw `AccessDeniedException` silently, leaving the old file on disk. The clear logic was correct; the IO transport was not. | Replaced `ExaniraEventSavedData` entirely with `PendingEventAttachment` — an `AttachmentType.serializable` registered in `CharacterAttachment`. Attachment data is written directly into the player's own NBT (`playerdata/UUID.dat`) by Minecraft's synchronous player save mechanism. No temp files, no async renames, no race conditions. Per-world automatically (each world has its own `playerdata/` folder). |

---

## Known Issues / Gotchas

- **`AddReloadListenerEvent` is a game-bus event** — must be registered on `NeoForge.EVENT_BUS`, not `modEventBus`. Registering on the wrong bus crashes with `IModBusEvent` mismatch on startup.
- **`EventHudRenderer` / `EventInputHandler` deleted** — early prototype used a non-pausing HUD overlay; discarded because the mouse was not captured. Use `EventScreen` (proper Screen subclass) opened via Radio right-click instead.
- **Radio glow uses `CUSTOM_DATA` NBT** (`active: true` / `false`), set server-side in `EventQueueManager.setRadioActive()`. Syncs to client automatically via vanilla inventory updates — no extra packet needed.

---

## Phase 3 Preview — Multiplayer Logic

- Temporary party formation (all players assigned to same event)
- Event locking (player can only be in one side event at a time)
- Shared decision resolution + party vote UI
- Resolve the `successEvent` chain (currently just logged)
- Automatic triggers (proximity / time-based / world-flag)
