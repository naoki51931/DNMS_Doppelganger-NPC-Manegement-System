# DNMS Advanced Simulation Roadmap (2026-11-21)

Status: **design and staged implementation, NOT seven completed features**. Minecraft 1.21.1 Java 21 build and in-game tests remain required.

## Invariants
- One shared, player-aligned personality for playable Nao and up to 24 Nao NPCs.
- Individual UUID, body, inventory, skills, relationships and journals; MIP-derived shared memory is an index, not a replacement for original journals.
- Reward Learning Patch remains OFF by default. Existing time-based MIP scheduling remains.
- Incremental growth adds one NPC at a time, up to 24; existing /doppel next rewinds the outer player and clock. Existing world changes are retained by default.
- Never claim a player switch is complete merely because /doppel select teleports a player to an armor stand.

## 1. Player-shaped NPCs
Replace armor-stand stand-ins with server-side human-like NPC entities and client-visible player skins. Keep UUID mapping stable and distinguish entity UUID from logical identity. Handle spawning, despawning, persistence, animation, collision and reconnect. Choose a compatible rendering approach and validate with Fabric 1.21.1. No fake online player accounts.

## 2. Mining, building and item acquisition learning
Separate goals from actual executable actions. Introduce action outcomes (block mined, item acquired, block placed, failed) and skill updates only on confirmed success. Respect item stack components, tool rules, world permissions and protection checks. Building protection and pathfinding were deferred earlier; do not silently declare them finished.

## 3. Conversation, romance and relationships
Add consent-aware social interactions among simulated adult NPCs, relationship scores, trust, familiarity and reversible dialogue choices. Distinguish friendship/romance from inferred real-world feelings. Save relationships by logical UUID; record interaction events in individual journals; MIP may index them. No claim that an LLM is connected until a provider exists.

## 4. Optional full-world rollback
Add a separate opt-in WORLD_SNAPSHOT mode; default remains PLAYER_AND_CLOCK_ONLY. Snapshots must cover affected dimensions/chunks, block entities, entities, inventory, time, weather and persistent state with crash-safe checkpoints. Stop concurrent world writes during restore. Do not attempt partial snapshots while calling them full rollback. Define memory persistence across rollback explicitly and test it.

## 5. Full control transfer
Replace teleport-only select with a control-session ownership model: suspend autonomous AI, transfer camera/input/action authority to the chosen NPC, preserve prior playable state, return control safely, prevent duplicate inventory or simultaneous writers. Test logout, death, dimension change and reconnect.

## 6. Integrated memory decisions
Use MIP-derived events for retrieval and grounded action ranking, with timestamps, locations, provenance, stale-memory handling, deterministic fallback and no double counting. Equal per-experience weighting across all Nao sources; document difference from equal per-Nao weighting.

## 7. Personality growth
Introduce a versioned, reviewable personality proposal layer derived from repeated experience; never overwrite the playable Nao's authoritative preferences without explicit approval. Keep history, provenance, rollback and a default OFF switch.

## Suggested implementation order and acceptance criteria
0. Make Fabric 1.21.1 / Java 21 CI build green and fix save/restore failures before destructive world operations.
1. NPC identity/skin rendering: 24 visible, distinct, persistent logical IDs after restart.
2. Complete action execution and confirmed-outcome learning: mining, collection, building; no rewards on failed actions.
3. Control transfer: one active controller, round-trip state restored after reconnect.
4. Shared-memory decision retrieval: deterministic test cases, stale handling.
5. Social graph and dialogue: persistent relationships, opt-in romance, no unwanted automatic relationship escalation.
6. Personality growth proposals: default OFF and reversible.
7. Full-world snapshot rollback: opt-in and isolated from default growth loop; integrity and crash tests.

## Deferred and known risks
- Protection of player-built structures during harvesting: deferred, unsafe in current collector.
- Pathfinding to gathering targets: deferred.
- Checkpoint atomicity, item data migration, generation pruning and corruption validation require verification.
- No runtime/CI pass has been verified in this planning document.
