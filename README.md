# Doppel NPC Management System (DNMS)

**DNMS** (formerly Doppelganger NPC System) is an experimental Minecraft Java Edition **Fabric 1.21.1** server mod plus C++ console prototypes for a 24-character daily loop.

## Fabric mod (early MVP)

The Java mod records one player's positions across repeated simulated days and uses per-character movement habit models to move armor-stand NPC stand-ins autonomously (or replays recorded tracks). This is **not yet a complete Minecraft NPC or rollback implementation**.

### Build

Requires Java 21 and Gradle 8.x (no Gradle wrapper is included yet).

```bash
gradle build
```

Copy the generated `build/libs/doppelganger-npc-system-0.1.0.jar` into a compatible Fabric 1.21.1 server's `mods/` folder with Fabric API. Build and runtime compatibility have **not yet been verified**.

### Commands (operator permission required)

- `/doppel start` — begin day 1 loop 1 and capture the player's position every server tick
- `/doppel next` — finish the current recording, reset the clock and player position, and begin the next loop
- `/doppel status` — show progress
- `/doppel select 1` through `/doppel select 24` — after 24 recordings, teleport the player to the selected recorded ghost's current replay position
- `/doppel emotion curious` — during training, label the player's current self-reported emotion; choices: `neutral`, `happy`, `curious`, `afraid`, `angry`, `sad`
- `/doppel reason explore` — during training, label the player's current self-reported reason; choices: `none`, `explore`, `build`, `collect`, `safety`, `social`
- `/doppel preference explore 9` — set shared Nao personality affinity for a reason from 0 to 10 (also: `none`, `build`, `collect`, `safety`, `social`)
- `/doppel ai on` — use learned movement habits for autonomous NPC movement (default)
- `/doppel ai off` — switch to exact recorded-position replay
- `/doppel stop` — stop and discard in-memory recordings

The recording window is at most 24,000 ticks (one standard Minecraft day) per character. `next` can be invoked before a full day is recorded; for complete daily tracks, wait until the day ends.

### Important limitations

- **No real rewind of the world**: block edits, mobs, weather, player inventory and other game state are NOT restored. Only the clock and player's position are reset.
- NPC stand-ins are armor stands driven by learned directional frequencies conditioned on day/night and the player's explicitly labeled emotion and reason. They are not full player models, pathfinding agents, or language-model AI.
- Emotions and reasons are player-supplied annotations, not detected mental states. The last supplied labels persist for the rest of that character's training and later AI behavior. The model does not independently infer changing emotions, nor does it guarantee perfect preference matching.
- The current policy learns movement direction only. The reason label `build` does not teach actual building, and `collect` does not teach gathering.
- The learned movement policy has no inventory, mining, building, social behavior or robust collision avoidance.
- All 24 NPCs use **one shared personality profile** as an affinity weighting system, plus the combined evidence of all characters' demonstrations. Each character's own memory is weighted twice as heavily when selecting its action. This is a heuristic, not full psychological or semantic understanding.
- `/doppel preference` is the player's explicit preference, not an automatically inferred psychological trait. Emotions and reasons are likewise explicitly labeled.
- The Markdown files are a readable structured journal, **not** an LLM memory retrieval system. Only structured movement samples are parsed for decisions. Free-form notes are not yet interpreted.
- Recorded movement observations are sampled every 20 ticks (approximately once per second); only samples flushed to disk are durable. The stand-ins are not real controllable player entities.
- The `select` command teleports the operator; it does not transfer control to a real NPC or suppress the selected ghost.
- Recorded position tracks are held in memory and lost when the server stops. However, sampled movement observations and their player-labeled emotions/reasons are appended to individual Markdown journals on `/doppel next` and `/doppel stop`, and are reloaded into the learner on `/doppel start`. A crash before flushing may lose recent samples.
- Multiplayer, dimension changes, restarts, collisions, death, and chunk loading are not yet robustly handled.
- Basic movement imitation is integrated in the Fabric mod; the richer C++ learner remains a separate demo.

## C++ console prototypes

`src/doppel_integrated.cpp`: 24 rounds of simplified habit learning and free switching.

`src/doppel_day_loop.cpp`: deterministic same-day recording and merge simulation.

```bash
g++ -std=c++17 -O2 src/doppel_integrated.cpp -o doppel
./doppel
g++ -std=c++17 -O2 src/doppel_day_loop.cpp -o dayloop
./dayloop 30
```

Other demos: `src/doppel_npc.cpp`, `src/doppel_switch.cpp`.

## Next development milestones

1. Build/test the Fabric mod and correct any API mismatches.
2. Implement reliable bounded-region world snapshots and rollback (including block entities), with safety limits.
3. Record and replay meaningful actions (movement, mining, placing, interactions), not only positions.
4. Replace armor stands with controllable NPCs and integrate learned habit policies.
5. Persist recordings and daily state; reconcile conflicting actions when 24 timelines merge.
6. Support switching control among all 24 NPCs while the other 23 continue autonomously.

## Shared personality and individual Markdown memory

The Fabric mod creates the following files on the **server** at `config/doppelganger/memories/` (not inside this public GitHub repository):

```text
config/doppelganger/memories/
  nao_personality.md   # one personality shared by all 24
  nao_01.md            # Nao 01 personal observations
  nao_02.md            # Nao 02 personal observations
  ...
  nao_24.md            # Nao 24 personal observations
```

Example in-game training:

```mcfunction
/doppel start
/doppel preference explore 9
/doppel preference safety 8
/doppel emotion curious
/doppel reason explore
```

Move around to teach Nao 01, then type `/doppel next` to flush her memory and move to Nao 02. Repeat through Nao 24. Each character's Markdown journal is separate, but their action policy references the same `nao_personality.md`. The decision score combines shared demonstrations, preference affinity and double weight for the current NPC's own experiences.

**Privacy:** journals contain recorded in-game positions and player-supplied emotional labels. Keep the server files private if you do not want to publish them.

## Optional patch: Memory Integration

The standalone **[Memory Integration Patch](patches/memory-integration/README.md)**
lives under `patches/memory-integration/`, with implementation guidance in
`patches/memory-integration/AGENTS.md`.

It reads the 24 individual `nao_XX.md` journals and the authoritative
`nao_personality.md`, then generates an attributed shared index
`nao_integrated_memory.md`. The source journals and shared personality are
not overwritten. This is an **offline Java 21 tool**, not yet invoked by the
Fabric mod or consumed by live NPC decision-making.

From the repository root:

```bash
javac -d patches/memory-integration/out patches/memory-integration/src/org/nagineco/patch/memoryintegration/MemoryIntegrationPatch.java
java -cp patches/memory-integration/out org.nagineco.patch.memoryintegration.MemoryIntegrationPatch config/doppelganger/memories
```


### MIP automatic mode (default ON)

The Memory Integration Patch is compiled into the Fabric mod by `build.gradle`.
When `/doppel start` initializes the memory store, MIP automatically generates
`nao_integrated_memory.md`. After each `/doppel next` or `/doppel stop`,
the mod flushes individual journals and re-generates the integrated index.
These operations log to the server console by default.

Operator commands:

```mcfunction
/doppel mip status
/doppel mip logs
/doppel mip run
/doppel mip off
/doppel mip on
```

`off` disables automatic integration without deleting individual memories.
`on` persists the setting and integrates immediately when memory storage is
initialized. The toggle persists at `config/doppelganger/mip.enabled`;
if absent, MIP defaults to ON. `run` requires MIP to be ON.

Logs are emitted to the server console and the last 20 messages can be
retrieved using `/doppel mip logs`. This is not a per-event debug log.
The patch is still a structured Markdown integration utility, **not** a live
semantic memory reader: NPC decision code does not yet consume the integrated
index. Build and Minecraft runtime remain unverified.


## Daily Minecraft-time schedule

MIP now automatically integrates **once per in-game day at 00:00 (midnight)** by
default, provided the Doppel system has been started and MIP is ON. In Minecraft
Java, midnight is **tick 18000**; tick 0 is **06:00**, not midnight.

Set a different time (24-hour Minecraft clock):

```mcfunction
/doppel mip time
/doppel mip time 00:00
/doppel mip time 12:30
/doppel mip status
/doppel mip logs
```

The schedule is saved in `config/doppelganger/mip.time` and survives restarts.
Automatic integration flushes pending individual memory samples first and logs
the outcome to the server console and `/doppel mip logs`.

**Scheduling semantics:** it triggers on normal forward crossings of the
configured clock time; it does not replay missed triggers after large time
jumps. It runs at most once per underlying Minecraft day number. Rewinding the
clock for the 24 training loops does not trigger the same day again. With the
existing prototype's repeated clock resets, this means the daily merge may
happen once across multiple training loops, not once for each loop. Use
`/doppel mip run` for an immediate merge. Turning MIP OFF suppresses
scheduled merges without deleting memories. `/doppel next` and
`/doppel stop` now flush journals but do not themselves schedule a merge.

## Playable Nao automatic observations and NPC reports

The playable Nao is the primary demonstration source. During an active session, DNMS automatically samples inventory changes and health changes every 20 ticks (about one second), alongside existing movement observations. Positive inventory changes are recorded as `item_gain`, decreases as `item_loss`, and health reductions as `damage` in the currently associated Nao's individual Markdown journal. These are observed changes, **not proof of mining, crafting, combat, or the reason for a change**. The system does not infer emotions or motivations from these events.

- `/doppel logs` — view up to 100 recent in-game report entries, including observed item gains and damage.
- `/doppel npc report` — request each active stand-in NPC's position and movement sample count; results are also added to the report log.
- `/doppel mip logs` — separate MIP integration diagnostics.

Reports are delivered as operator command feedback and server console messages. They are **not yet unsolicited NPC messages**. The report ring buffer resets on server restart and on `/doppel start`; individual Markdown observations persist when flushed. Inventory and health sampling is best-effort: changes that cancel out between samples can be missed. NPCs still cannot mine, build, converse, or learn new gameplay actions from these journal event types. Integrated Markdown memory is not yet fed back into live NPC decisions.

## Automatic Movement Patch (AMP)

The opt-in [Automatic Movement Patch](patches/automatic-movement/README.md) lets the playable Nao select horizontal movement using the **shared Nao personality** and existing movement habit models. Enable with `/doppel automove on`, disable immediately with `/doppel automove off`, inspect `/doppel automove status`, and view periodic choices with `/doppel logs`. Default is OFF. This is movement imitation, not full autonomous Minecraft gameplay. It does not have pathfinding, ledge safety, mining, building or combat; test only in a safe area. Implementation guidance: [AMP AGENTS.md](patches/automatic-movement/AGENTS.md). Build/runtime unverified.
