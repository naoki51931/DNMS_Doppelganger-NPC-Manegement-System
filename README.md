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
- `/doppel ai on` — use learned movement habits for autonomous NPC movement (default)
- `/doppel ai off` — switch to exact recorded-position replay
- `/doppel stop` — stop and discard in-memory recordings

The recording window is at most 24,000 ticks (one standard Minecraft day) per character. `next` can be invoked before a full day is recorded; for complete daily tracks, wait until the day ends.

### Important limitations

- **No real rewind of the world**: block edits, mobs, weather, player inventory and other game state are NOT restored. Only the clock and player's position are reset.
- NPC stand-ins are armor stands driven by simple learned directional frequencies (day/night), not full player models, pathfinding agents, or language-model AI.
- The learned movement policy has no inventory, mining, building, social behavior or robust collision avoidance. The stand-ins are not real controllable player entities.
- The `select` command teleports the operator; it does not transfer control to a real NPC or suppress the selected ghost.
- Recordings are held in memory and lost when the server stops or the mod is stopped.
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
