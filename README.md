# Doppelganger NPC System

Experimental Minecraft Java Edition **Fabric 1.21.1** server mod plus C++ console prototypes for a 24-character daily loop.

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
