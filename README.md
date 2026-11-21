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
- `/doppel stop` — save recordings to JSON and stop the session (does not discard recordings)
- `/doppel save` — explicitly save the active session to JSON
- `/doppel resume` — restore a saved session from JSON

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
- Recorded position tracks are held in memory while active and are persisted in `config/doppelganger/recordings.json` on `/doppel stop`, `/doppel next`, `/doppel save`, and periodic checkpoints (every 1200 ticks). Abrupt crashes may still lose changes since the last successful save. However, sampled movement observations and their player-labeled emotions/reasons are appended to individual Markdown journals on `/doppel next` and `/doppel stop`, and are reloaded into the learner on `/doppel start`. A crash before flushing may lose recent samples.
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
not overwritten. The patch can run as a **standalone Java 21 tool** and is also compiled into the Fabric mod. Its integrated index is not yet consumed by live NPC decision-making.

From the repository root:

```bash
javac -d patches/memory-integration/out patches/memory-integration/src/org/nagineco/patch/memoryintegration/MemoryIntegrationPatch.java
java -cp patches/memory-integration/out org.nagineco.patch.memoryintegration.MemoryIntegrationPatch config/doppelganger/memories
```


### MIP automatic mode (default ON)

The Memory Integration Patch is compiled into the Fabric mod by `build.gradle`.
MIP generates `nao_integrated_memory.md` on scheduled in-game time crossings (default 00:00), on `/doppel mip run`, and when MIP is enabled with `/doppel mip on` while a memory store exists. `/doppel start` initializes the memory store but does not itself merge; `/doppel next` and `/doppel stop` save Markdown journals and JSON snapshots without triggering integration. Integration events log to the server console.

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

The opt-in [Automatic Movement Patch](patches/automatic-movement/README.md) lets the playable Nao select horizontal movement using the **shared Nao personality** and existing movement habit models. Enable with `/doppel automove on`, disable immediately with `/doppel automove off`, inspect `/doppel automove status`, and view periodic choices with `/doppel logs`. Default is OFF. This is movement imitation, not full autonomous Minecraft gameplay. It does not have pathfinding, guaranteed ledge safety, mining, building or combat; test only in a safe area. Implementation guidance: [AMP AGENTS.md](patches/automatic-movement/AGENTS.md). Build/runtime unverified.

### AMP safety update

- **Self-training prevention:** While `/doppel automove on` is active, generated player movement is excluded from `HabitBrain.observe` and the structured movement journal, preventing an automatic movement feedback loop. Other directly observed events (inventory and health) remain available. Manual training resumes after `/doppel automove off`; the previous observation anchor is reset to the player's current position to prevent a false movement sample across the mode switch.
- **Safer movement:** AMP now requires grounded movement, checks the destination player's collision box, verifies support underfoot, and rejects fluid destinations. These are conservative safeguards, **not full pathfinding or guaranteed fall protection**. Avoid testing near cliffs or hazardous terrain until verified.
- Fabric 1.21.1 build and runtime have not yet been validated.

## JSON recording persistence

The Fabric mod flushes all pending individual Markdown journals **before** writing the JSON snapshot at each save checkpoint. The JSON file stores all recorded Nao movement tracks, current day, selected Nao and free-play state to `config/doppelganger/recordings.json`. The file is written through a temporary file followed by a replacement move to reduce partial writes. `/doppel stop` saves before clearing in-memory data and refuses to stop if the save fails. `/doppel save` forces a checkpoint; `/doppel resume` reloads saved tracks and restores each Nao's learned model from its existing Markdown journal. `/doppel start` begins a **new** session and may replace the previous snapshot at the next save; use `resume` to continue an existing session. Snapshots also occur every 1200 ticks during active sessions and on `/doppel next`.

The JSON snapshot does not include the entire Minecraft world, NPC entities, inventories or a complete rewind. After resume, ghost stand-ins are recreated from tracks, automatic movement defaults OFF, and movement habits are restored from Markdown (not from a serialized in-memory brain). Crash recovery is limited to the last successful checkpoint. Build and Minecraft runtime are still unverified.

## Signed gameplay observations and shared personality invariant

- Inventory gains are recorded as `event=item_gain detail=score=+N item=...`; observed health loss as `event=damage detail=score=-N health=...`. Inventory losses are recorded as `event=item_loss detail=score=0 count=-N item=...`, because consumption is not necessarily a negative outcome. Scores are **recorded annotations only** and do not yet modify movement preferences or choose actions.
- The Markdown movement parser explicitly ignores `event=` lines lacking `action=`, so reward/damage events cannot become accidental `STAY` training samples.
- AMP mode transitions reset the current Nao brain's observation anchor to avoid training on movement performed by AMP. Manual observations resume after AMP is disabled.
- **Do not split the personality:** playable Nao and all 24 NPC Nao share the same authoritative `nao_personality.md`. Individual journals are experiences, not separate personalities. No change to that invariant is intended.
- Every JSON checkpoint first flushes pending Markdown events. The JSON and Markdown are still separate files and are not a single atomic transaction; a crash between writes can leave a partially advanced Markdown journal. A fully transactional snapshot would require a further versioned manifest/rollback design.

## Checkpoint and recovery corrections

- Periodic JSON checkpoints are paced by **server ticks** (at most once per 1200 ticks), not by the training loop counter, which can pause at 24000 and previously cause repeated writes.
- On `/doppel resume` during training, the player is teleported to the last recorded position of the active Nao and the movement observation anchor is reset there. This is **not** a world or inventory rollback. The saved track may have ended before the latest Markdown event flush in a crash.
- In free-play mode, `/doppel next` checkpoints the current day **before** advancing the day counter; a failed save no longer advances the day.
- Signed item/damage scores are journal annotations, **not yet reward-trained actions**. MIP's clock schedule still operates on Minecraft time, not a guaranteed once-per-training-loop merge. The shared playable/NPC Nao personality remains a single profile.

## Optional Reward Learning Patch (RLP; default OFF)

The separately organized `patches/reward-learning/` module attributes observed item gains (+item count) and health damage (-health points) to the **last manually observed movement** of the active playable Nao. It records signed reward entries in the corresponding individual `nao_XX.md` journal and reloads them with the movement model. This is temporal attribution, **not proof that the movement caused the outcome**. Item loss is neutral. When enabled, reward weights influence both NPC shared movement decisions and AMP choices; when disabled, signed event logging continues but reward weights do not affect decisions. AMP-generated movements are excluded from reward attribution.

Commands (operator, playable owner): `/doppel reward on`, `/doppel reward off`, `/doppel reward status`. Default is **OFF**; the toggle is persisted in `config/doppelganger/reward-learning.enabled`. The shared `nao_personality.md` remains authoritative for the playable Nao and all 24 NPC Nao. This patch does not introduce independent personalities. Build/runtime verification is pending.
