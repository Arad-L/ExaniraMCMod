# Phase 3 Execution — Multiplayer Logic

**Goal**: Multiple players can share the same event instance, decisions are resolved as a group vote, the event lock system is enforced with visible feedback, and the `successEvent` cross-event chain is fully functional.  
No main story forced-join. No global story flags. Multiplayer side events only.

**Status**: 🟡 In Progress — event locking + successEvent chain completed in Phase 2 continuation; party formation and vote UI not yet built

| # | Task | Status | Notes |
|---|------|--------|-------|
| 3.1 | Event locking (side events) | 🟢 Complete | `playerToEvent` map in `EventQueueManager` blocks double-join; red chat message shown to player |
| 3.0b | Debug stop command | 🟢 Complete | `/exanira event stop` (self) and `/exanira event stop <player>` (target) — op level 2; calls `forceStopEvent()` |
| 3.2 | `successEvent` cross-event chain | 🟢 Complete | Choices with no `nextScene` can set `successEvent`; `endEvent()` calls `startEvent()` on chain |
| 3.3 | Multi-scene event structure | 🟢 Complete | Events are `Map<sceneId, EventScene>`; `nextScene` field advances within event; terminal scenes auto-dismiss |
| 3.4 | Offline auto-resolve + persist | 🟢 Complete | `PendingEventAttachment` (player NBT) stores `eventId` + `currentSceneId`; `resyncPlayerIfMidEvent()` reconstructs in-memory state on login; per-world automatically via `playerdata/` isolation |
| 3.5 | Temporary party formation | 🔴 Not started | Multiple players start the same event → share one `ActiveEvent` instance; all see the same dialogue |
| 3.6 | Shared decision / party vote UI | 🔴 Not started | All party members vote; majority wins (or unanimity required — TBD); vote UI in `EventScreen` |
| 3.7 | Party vote packet | 🔴 Not started | `PartyVotePacket` C→S; server collects votes, resolves when all members have voted |
| 3.8 | Vote result broadcast | 🔴 Not started | Server broadcasts chosen option + result to all party members via `EventStartPacket` (next scene) |
| 3.9 | Event lock release on logout mid-party | 🔴 Not started | If a party member logs out, their vote auto-resolves via `offlineFallback`; remaining members continue |
| 3.10 | End-to-end multiplayer test | 🔴 Not started | Two players, same event, vote on a choice, verify both see scene advance |

---

## Design Decisions Needed Before Implementation

| Question | Options | Status |
|---|---|---|
| Party formation trigger | `/exanira event start <id>` targets a party (all nearby? all online? named group?) | ❓ Undecided |
| Vote resolution rule | Majority wins vs. unanimous vs. first-to-vote | ❓ Undecided |
| Tie-break | Random pick vs. lowest-stat option vs. wait for timeout | ❓ Undecided |
| Max party size | Capped (e.g. 4) or unlimited? | ❓ Undecided |

---

## What "Temporary Party" Means

From PLANNING.md:
> Everyone participating in the same active event forms a **temporary party** for the duration of that event only. No persistent party system.

Concretely:
- Party = `ActiveEvent.participants` (already a `Set<UUID>`)
- Party exists only while the `ActiveEvent` is in `EventQueueManager.activeEvents`
- When the event ends, the party dissolves — no stored party state

The scaffolding (`participants` set, `ActiveEvent` instance shared by a key) is already in place. What's missing is:
1. A way to assign multiple players to the same `ActiveEvent` instance
2. A vote-collection mechanism on the server
3. A UI for showing other players' votes in `EventScreen`

---

## Already-Built Scaffolding (from Phase 2)

| Component | Relevance to Phase 3 |
|---|---|
| `ActiveEvent.participants` | Already a `Set<UUID>` — add more players to it for party support |
| `EventQueueManager.playerToEvent` | Maps `UUID → instanceKey`; enforce same instanceKey for party members |
| `EventQueueManager.startEvent()` | Currently creates a new `ActiveEvent` per player — needs party check |
| `EventScreen.refresh()` | Already calls `rebuildWidgets()` — reuse for vote state updates |
| `EventStartPacket` | Carries dialogue + choices to client — extend with vote state if needed |

---

## Files to Create in Phase 3

```
network/
    PartyVotePacket.java        — C→S: instanceKey + choiceIndex (player's vote)
    PartyVoteStatePacket.java   — S→C: current vote tally per choice (for UI display)

event/
    VoteCollector.java          — server-side: collects votes per instanceKey, resolves on completion
```

---

## Phase 4 Preview — Main Story System

- Global story flags (`SavedData` — world-persistent)
- Main story events: all online players force-joined automatically
- 5-minute accept timer; reschedule on timeout / mass decline
- **Open edge case**: if a player holds a side event lock when a main story event fires — behavior not yet defined (must resolve before Phase 4 implementation)
