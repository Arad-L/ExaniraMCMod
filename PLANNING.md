# Narrative Event Engine — Final System Architecture

> **Narrative Event Engine + RPG Character Layer + Horde Director** on top of Minecraft (NeoForge 1.21.1)

Three core subsystems:

---

# 1. Narrative Event Engine (THE CORE)

## State Model

- **Global Campaign State (Server-wide)** — main story progression, world flags (cities fallen, NPC alive/dead, etc.)
- **Party State** — everyone participating in the same active event forms a **temporary party** for the duration of that event only. No persistent party system.
- **Player State** — character sheet, stats, personal flags/backstory consequences

---

## Event Types

### Main Story Events (GLOBAL LOCKED)
- Only one active at a time
- All online players are **forced into the event automatically** — no opt-in prompt
- A **5-minute accept window** is shown. If not all players accept within 5 minutes (or all decline), the event is dismissed and rescheduled to attempt again later
- Blocks other main story progression while active

### Side Events (PARALLEL)
- Per-player or per-temporary-party
- Up to 3 simultaneous server-wide
- Players can only be in ONE side event at a time (event lock)

### Ambient Events
- Passive world narration
- No player lock
- Horde pressure / radio chatter / environmental storytelling

---

## Event Engine Loop

```
tick →
  check triggers →
    spawn event →
      assign players (or force-join for main story) →
        wait for input →
          resolve outcome (hard gate skill checks) →
            update state →
              clean up event
```
 
### Critical: EventQueueManager

> **WARNING**: The event system MUST be routed through a central `EventQueueManager`. Never attach event logic directly to individual player tick handlers. Per-player scattered logic causes multiplayer desync and duplicate event spawns.

The `EventQueueManager` owns:
- The active event list
- Player-to-event assignment map
- The event scheduling queue
- The 5-minute main story accept timer

### Phase 4 Edge Case (Unresolved)
> If a player already holds a side event lock when a main story event fires, behavior is not yet defined. Options: side event auto-resolves, pauses, or main story waits until lock clears. Must be decided before Phase 4 implementation.

---

## Offline Player Handling

If a player disconnects mid-event, their pending choices **auto-resolve** using a defined fallback strategy per event (e.g. `"ignore"`, `"flee"`, or a configurable default in the event JSON). The event continues for remaining players without pausing.

---

# 2. Character System (RPG LAYER)

## Character Sheet

### Core Stats
**Stat range: 1–10.** Values set at character creation. Upgradeable in-game up to the cap of 10.

| Stat | Description |
|---|---|
| Strength | Physical power, melee effectiveness |
| Agility | Speed, evasion, quick movement |
| Intelligence | Tech, problem-solving, crafting |
| Perception | Awareness, detection, loot quality |
| Leadership | Party bonuses, NPC relations |
| Survival | Endurance, foraging, wilderness |

### Derived Values (computed from stats)
- Stealth effectiveness — `AGILITY × 2`
- Loot quality bonus — `PERCEPTION + INTELLIGENCE`
- Horde detection range modifier — `PERCEPTION × 3` (blocks)
- Leadership derived effects — deferred to Phase 3

---

## Character Creation Flow

On **first login**, a blocking multi-step screen interrupts the player before they can move. The screen cannot be closed with Escape.

### Step 1: Profession Selection
Player chooses from 8 professions. Each profession sets a fixed stat preset (total: 20 points distributed across 6 stats):

| Profession | STR | AGI | INT | PER | LEAD | SUR |
|---|---|---|---|---|---|---|
| Soldier | 5 | 3 | 2 | 3 | 4 | 3 |
| Nurse | 2 | 3 | 5 | 4 | 3 | 3 |
| Mechanic | 4 | 3 | 4 | 2 | 2 | 5 |
| Teacher | 2 | 2 | 5 | 3 | 5 | 3 |
| Scavenger | 3 | 5 | 3 | 4 | 2 | 3 |
| Farmer | 4 | 2 | 2 | 3 | 2 | 7 |
| Radio Operator | 2 | 2 | 4 | 5 | 3 | 4 |
| Firefighter | 4 | 4 | 2 | 3 | 4 | 3 |

### Steps 2–6: Lifestyle Questions (5 questions)
Each question has 3 options. Each option adds **+1 to one specific stat** on top of the profession preset.

Questions (confirmed):
1. "How did you stay fit?" → STR / AGI / SUR
2. "What sharpened your mind?" → INT / PER / LEAD
3. "What did you do in your spare time?" → INT / SUR / STR
4. "When conflict arose, you..." → LEAD / PER / AGI
5. "What would others say about you?" → STR / PER / SUR

### Step 7: Confirmation
Player sees a confirmation message. Pressing **"Begin Your Story"** locks the choices and submits them to the server.

**No stat preview is shown during selection** — the final sheet is revealed after confirmation.

---

## Backstory Generation

Template engine using the player's actual selections. Key identity details (profession, physical background, hobby) are deterministic from choices. Minor flavor details (location, loss, distrust) are drawn randomly from pools on each character creation.

Template structure:
```
"You were a {profession} in {location}. You {physical_background}.
When you weren't working, you {hobby}.
When everything fell apart, you lost {loss}. Now you trust {distrust} the least."
```

Output stored in `CharacterSheet.backstory` (NBT via Data Attachments).

---

## Skill Checks — Hard Gates

Skill checks are **deterministic hard gates only**. No dice, no randomness:

```
success = (player.getStat(skill) >= requirement)
```

- If the player meets the threshold → option is available and succeeds
- If not → option is locked/greyed out in the UI
- Narrative tension comes from choices and consequences, not RNG

`checkType` field is present in the event schema to allow future expansion without breaking changes. Always `"hard"` for now.

---

# 3. Event System (HYBRID SCRIPTING)

## JSON defines:
- Event structure, type, dialogue, choices, triggers, stat requirements, offline fallback action

## Java handles:
- World effects, NPC spawning/despawning, loot generation, horde triggers, complex branching logic

## Example Event Schema

```json
{
  "id": "abandoned_radio_station",
  "type": "side",
  "npc": "HologramSurvivor",
  "dialogue": [
    "You hear static...",
    "A voice breaks through the radio..."
  ],
  "offlineFallback": "ignore",
  "choices": [
    {
      "text": "Respond to the signal",
      "requires": { "perception": 3 },
      "checkType": "hard",
      "successEvent": "safe_contact",
      "lockedText": "You lack the perception to read this situation."
    },
    {
      "text": "Ignore it",
      "outcome": "nothing_happens"
    }
  ]
}
```

---

# 4. Horde System (DIRECTOR LAYER)

> Hordes are a **dynamic world pressure mechanic**, not individual mob AI.

## Spawn Director

Increases spawn weight based on:
- Noise generated by players
- Time since last horde
- Player density in an area

## Horde States

`Dormant → Roaming → Tracking → Attacking → Dispersing`

## Limb System

> ⚠️ **OPEN DESIGN POINT — Implementation Not Planned**
>
> Whether zombie entities will be subclassed (custom entity types) or have data attached to vanilla entities via NeoForge Data Attachments is **not yet decided**. This affects whether speed/attack modifications are done via AI goal overrides or event hooks. This section must be revisited before Phase 5. Keep limb system implementation entirely separate from horde pressure logic so the two can be built independently.

---

# 5. NPC System

Event NPCs will use a **pre-existing NPC mod** compatible with NeoForge 1.21.1 that renders player-model entities (similar to fake player displays seen on multiplayer servers). The event system will call into that mod's API to spawn and despawn NPCs at event start/end. Dialogue triggering and interaction hooks will be implemented via NeoForge events rather than reimplemented from scratch.

> **TODO before Phase 6**: Evaluate NPC mod options. Requirements: NeoForge 1.21.1 compatibility, programmatic spawn/despawn API, player-skin-style rendering.

---

# 6. Event UI System

## Chat Layer (lightweight)
- Quick alerts, narration text, short prompts

## GUI Layer (main interaction)
- Choice buttons (locked/available state based on hard gate checks)
- Stat requirement displayed next to locked choices
- Party vote UI for co-op decisions (temporary party = everyone in the event)
- Main story 5-minute accept countdown display

## Event Locking
- Player receives an **event lock** on joining any side event
- Lock prevents joining other side events
- Lock is released on event resolution (success, failure, or player logout/auto-resolve)

---

# 7. Data Architecture (NeoForge 1.21.1)

| Concern | NeoForge API |
|---|---|
| Server-wide campaign state | `SavedData` |
| Character sheets / player data | `IAttachmentType<T>` (Data Attachments, registered via `NeoForgeRegistries.ATTACHMENT_TYPES`) |
| Active event state | `SavedData` + `EventQueueManager` |
| UI / networking | Custom network packets |

> **Note**: The old `ICapabilityProvider` pattern from pre-1.20.4 does **not** apply here. NeoForge 1.21.1 uses `AttachmentType` exclusively for attaching data to players and entities.

---

# MVP Build Order

## Phase 1 — Foundation
- [🟢 Complete] Player character sheet (NBT + Data Attachments)
- [🟢 Complete] Core stat system
- [🟢 Complete] MADLIBS backstory generator
- [🟢 Complete] Simple GUI to display character sheet

## Phase 2 — Event Engine (minimum)
- [🟢 Complete] JSON event loader
- [🟢 Complete] `EventQueueManager`
- [🟢 Complete] Event trigger (command-only: `/exanira event start <id>`; automatic triggers deferred to Phase 3+)
- [🟢 Complete] Choice UI (hard gate locks, stat badge shown on all choices)
- [🟢 Complete] Event state tracking + offline auto-resolve (reconnect resync implemented; multi-player test deferred to Phase 3)

## Phase 3 — Multiplayer Logic
- [ ] Temporary party formation (event-scoped)
- [ ] Event locking system
- [ ] Shared decision resolution + party vote UI

## Phase 4 — Main Story System
- [ ] Global story flags
- [ ] Forced join with 5-minute accept timer
- [ ] Reschedule logic on timeout / mass decline
- [ ] Resolve Phase 4 edge case (side event lock conflict with forced main story join)

## Phase 5 — Horde Director
- [ ] Spawn pressure system
- [ ] Horde states (Dormant → Roaming → Tracking → Attacking → Dispersing)
- [ ] World intensity scaling
- [ ] *(Limb system deferred — see open design point above)*

## Phase 6 — NPC Integration
- [ ] Select and integrate NPC mod
- [ ] Programmatic spawn/despawn during events
- [ ] Dialogue trigger hooks

## Phase 7 — Instanced Locations
> **Feature idea (not yet designed)**: Events that give the player coordinates (e.g. "warehouse two kilometres east") should resolve to a real pre-existing location in the world that has NPCs and lootable containers. On event completion, the player gains access to an **instanced copy** of that location through an interactable door or portal. The instance gives extra loot, unique NPC encounters, or continuation dialogue that the uninstanced version does not. Instance is per-player (or per-party), private, and destroyed on exit.
>
> Design questions to resolve before Phase 7:
> - How are pre-existing locations registered? (JSON definition with coordinates + dimension? Datapack structure?)
> - How is the instance created? (Copy of a JigsawStructure? Separate dimension per instance? Temporary chunk region?)
> - What triggers the door/portal to become active? (Event reward flag set on player's attachment data? World flag?)
> - Are instances persistent (survives disconnect) or ephemeral (destroyed after session)?
> - Maximum simultaneous instances per server?

## Phase 8 — Event Rewards & Consequences

> **Feature idea (not yet fully designed)**: Events should provide rewards and consequences based on both the player's choices and their overall performance during the event. Outcomes are not strictly success or failure; players may achieve full success, partial success, or failure, each resulting in different rewards, penalties, or follow-up opportunities. Rewards are intended to reflect the story being told by the event rather than being generated from generic loot pools.
>
> Event outcomes may grant rewards immediately on completion or direct the player to a follow-up location where the reward can be claimed through exploration or an additional encounter. Different choices within the same event may lead to entirely different rewards, NPC interactions, hostile encounters, or opportunities, even if the event reaches the same success state.
>
> Failed outcomes should not always result in receiving nothing. Depending on the event, failure may still create scavenging opportunities such as abandoned supplies, survivor corpses, or other story-specific discoveries. Poor decisions may also result in injuries, resource loss, missed opportunities, or combat encounters. Certain events may be designated as **Dangerous**, allowing player character death if critical mistakes are made.
>
> Design questions to resolve before Phase 8:
>
> - What standard outcome tiers should exist? (Success, partial success, failure, perfect success, etc.)
> - How are reward paths defined within event data?
> - Should rewards be attached to specific choices, final outcomes, or both?
> - How should follow-up location rewards be tracked and delivered?
> - How frequently should events grant rewards immediately versus through a follow-up encounter?
> - Should dangerous events provide greater rewards to compensate for increased risk?
> - How are injuries, resource losses, and other penalties balanced against reward value?
> - Should failed outcomes always provide some form of reward or opportunity?
> - How should NPC encounters, hostile encounters, and reward opportunities be represented within event outcome data?