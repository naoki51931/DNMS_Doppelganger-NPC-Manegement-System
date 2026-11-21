# AGENTS.md — Doppel NPC Management System (DNMS)

## Repository architecture

- `src/main/java/org/nagineco/doppel/`: experimental Fabric 1.21.1 server mod.
- `src/doppel_*.cpp`: independent C++17 console prototypes.
- `patches/memory-integration/`: standalone **Memory Integration Patch**.
  Read its own `README.md` and `AGENTS.md` before editing that directory.

## Design invariants

The project models 24 Nao characters with **one shared player-aligned personality**
and **24 distinct individual memory journals**. Never silently conflate their
individual experiences, infer emotions as fact, or overwrite the player's
authoritative personality during memory integration.

`nao_personality.md` is shared; `nao_01.md`–`nao_24.md` are independent;
`nao_integrated_memory.md` is a regenerable derived index.

## Development safety

Do not claim Minecraft integration, world rollback, actual NPC possession,
full emotion understanding, or verified build/test results without evidence.
Keep generated server memory files out of the public repository.
Do not double-count demonstrations when combining journals and live AI models.


## MIP default behavior

The Fabric mod compiles `patches/memory-integration/src` into its main
source set. MIP defaults ON, persists an explicit on/off toggle in
`config/doppelganger/mip.enabled`, and integrates at the configured Minecraft-time crossing, on `/doppel mip run`, or when `/doppel mip on` is issued with an initialized memory store.
Commands: `/doppel mip status|logs|run|on|off`. Keep MIP disabled behavior
non-destructive; do not erase journals or personality. Server logs and the
20-entry in-memory command log should explain failures without exposing private
memory contents. Do not double-load the generated index into the existing
brains.


## Daily MIP scheduling contract

MIP default schedule is Minecraft **00:00** (tick **18000**), not tick 0.
The operator can inspect or change it with `/doppel mip time [HH:mm]`.
Persist the setting in `config/doppelganger/mip.time`. Scheduled integration
requires MIP ON, an active Doppel session and initialized memory storage.
Trigger on forward time crossings, at most once per underlying Minecraft day,
and do not replay missed times after large clock jumps or repeated rewinds.
Flush individual journals before integration. Manual `/doppel mip run` remains
available. `/doppel next` and `/doppel stop` flush but do not merge on their
own. Note that the prototype rewinds the Minecraft clock repeatedly and
therefore a single Minecraft day ID may cover multiple training loops.

## Playable Nao automatic observations and NPC reports

The playable Nao is the primary demonstration source. During an active session, DNMS automatically samples inventory changes and health changes every 20 ticks (about one second), alongside existing movement observations. Positive inventory changes are recorded as `item_gain`, decreases as `item_loss`, and health reductions as `damage` in the currently associated Nao's individual Markdown journal. These are observed changes, **not proof of mining, crafting, combat, or the reason for a change**. The system does not infer emotions or motivations from these events.

- `/doppel logs` — view up to 100 recent in-game report entries, including observed item gains and damage.
- `/doppel npc report` — request each active stand-in NPC's position and movement sample count; results are also added to the report log.
- `/doppel mip logs` — separate MIP integration diagnostics.

Reports are delivered as operator command feedback and server console messages. They are **not yet unsolicited NPC messages**. The report ring buffer resets on server restart and on `/doppel start`; individual Markdown observations persist when flushed. Inventory and health sampling is best-effort: changes that cancel out between samples can be missed. NPCs still cannot mine, build, converse, or learn new gameplay actions from these journal event types. Integrated MIP's derived Markdown index is consulted by the experimental life AI when enabled, but not fed back into HabitBrain movement training.

## Automatic Movement Patch (AMP)

The opt-in [Automatic Movement Patch](patches/automatic-movement/README.md) lets the playable Nao select horizontal movement using the **shared Nao personality** and existing movement habit models. Enable with `/doppel automove on`, disable immediately with `/doppel automove off`, inspect `/doppel automove status`, and view periodic choices with `/doppel logs`. Default is OFF. This is movement imitation, not full autonomous Minecraft gameplay. It does not have pathfinding, guaranteed ledge safety, mining, building or combat; test only in a safe area. Implementation guidance: [AMP AGENTS.md](patches/automatic-movement/AGENTS.md). Build/runtime unverified.

### AMP safety update

- **Self-training prevention:** While `/doppel automove on` is active, generated player movement is excluded from `HabitBrain.observe` and the structured movement journal, preventing an automatic movement feedback loop. Other directly observed events (inventory and health) remain available. Manual training resumes after `/doppel automove off`; the previous observation anchor is reset to the player's current position to prevent a false movement sample across the mode switch.
- **Safer movement:** AMP now requires grounded movement, checks the destination player's collision box, verifies support underfoot, and rejects fluid destinations. These are conservative safeguards, **not full pathfinding or guaranteed fall protection**. Avoid testing near cliffs or hazardous terrain until verified.
- Fabric 1.21.1 build and runtime have not yet been validated.

## JSON recording persistence

The Fabric mod flushes all pending individual Markdown journals **before** writing the JSON snapshot at each save checkpoint. The JSON file stores all recorded Nao movement tracks, current day, selected Nao and free-play state to `config/doppelganger/recordings.json`. The file is written through a temporary file followed by a replacement move to reduce partial writes. `/doppel stop` saves before clearing in-memory data and refuses to stop if the save fails. `/doppel save` forces a checkpoint; `/doppel resume` reloads saved tracks and restores each Nao's learned model from its existing Markdown journal. `/doppel start` begins a **new** session and may replace the previous snapshot at the next save; use `resume` to continue an existing session. Snapshots also occur every 1200 ticks during active sessions and on `/doppel next`.

The legacy recordings JSON does not include world blocks or NPC entities. Generation checkpoints additionally contain a player inventory snapshot and Nao identity data, but still do not rewind the world. After resume, ghost stand-ins are recreated from tracks, automatic movement defaults OFF, and movement habits are restored from Markdown (not from a serialized in-memory brain). Crash recovery is limited to the last successful checkpoint. Build and Minecraft runtime are still unverified.

## Signed gameplay observations and shared personality invariant

- Inventory gains are recorded as `event=item_gain detail=score=+N item=...`; observed health loss as `event=damage detail=score=-N health=...`. Inventory losses are recorded as `event=item_loss detail=score=0 count=-N item=...`, because consumption is not necessarily a negative outcome. Scores are annotations when reward learning is OFF; when `/doppel reward on` is enabled, reward entries also affect learned movement choices.
- The Markdown movement parser explicitly ignores `event=` lines lacking `action=`, so reward/damage events cannot become accidental `STAY` training samples.
- AMP mode transitions reset the current Nao brain's observation anchor to avoid training on movement performed by AMP. Manual observations resume after AMP is disabled.
- **Do not split the personality:** playable Nao and all 24 NPC Nao share the same authoritative `nao_personality.md`. Individual journals are experiences, not separate personalities. No change to that invariant is intended.
- Every JSON checkpoint first flushes pending Markdown events. The JSON and Markdown are still separate files and are not a single atomic transaction; a crash between writes can leave a partially advanced Markdown journal. A fully transactional snapshot would require a further versioned manifest/rollback design.

## Checkpoint and recovery corrections

- Periodic JSON checkpoints are paced by **server ticks** (at most once per 1200 ticks), not by the training loop counter, which can pause at 24000 and previously cause repeated writes.
- On `/doppel resume` during training, the player is teleported to the last recorded position of the active Nao and the movement observation anchor is reset there. For generation snapshots with `player.json`, player inventory and other selected state are restored; the Minecraft world is not rolled back. The saved track may have ended before the latest Markdown event flush in a crash.
- In free-play mode, `/doppel next` checkpoints the current day **before** advancing the day counter; a failed save no longer advances the day.
- Signed item/damage events are annotations by default; with `/doppel reward on`, their associated reward entries influence movement decisions. MIP's clock schedule still operates on Minecraft time, not a guaranteed once-per-training-loop merge. The shared playable/NPC Nao personality remains a single profile.

## Optional Reward Learning Patch (RLP; default OFF)

The separately organized `patches/reward-learning/` module attributes observed item gains (+item count) and health damage (-health points) to the **last manually observed movement** of the active playable Nao. It records signed reward entries in the corresponding individual `nao_XX.md` journal and reloads them with the movement model. This is temporal attribution, **not proof that the movement caused the outcome**. Item loss is neutral. When enabled, reward weights influence both NPC shared movement decisions and AMP choices; when disabled, signed event logging continues but reward weights do not affect decisions. AMP-generated movements are excluded from reward attribution.

Commands (operator, playable owner): `/doppel reward on`, `/doppel reward off`, `/doppel reward status`. Default is **OFF**; the toggle is persisted in `config/doppelganger/reward-learning.enabled`. The shared `nao_personality.md` remains authoritative for the playable Nao and all 24 NPC Nao. This patch does not introduce independent personalities. Build/runtime verification is pending.

## Selected persistence, schedule and shared learning policy (2026-11)

- **Generation checkpoints:** each successful save writes `recordings.json`, `player.json`, the single shared `nao_personality.md`, and all 24 `nao_XX.md` files to a new `config/doppelganger/checkpoints/generation_*/` directory. A `COMPLETE` marker is written before the `CURRENT` pointer is atomically replaced. `/doppel resume` reads the committed generation; legacy `recordings.json` remains a fallback when no generation pointer exists. Interrupted unpublished generations are ignored. Previous generations are retained (no automatic pruning yet).
- **Player state:** checkpoint includes the playable Nao's dimension identifier, exact position/rotation, health, XP level/total/progress, and full inventory ItemStack NBT (item IDs/counts plus components). On `/doppel resume` these values replace the current player state. **Important limitation:** item stacks are serialized as complete registry-aware Minecraft NBT in v2 checkpoints, including enchantments, custom names, durability and other supported data components. Older v1 checkpoints only contain item IDs and counts, so their missing metadata cannot be reconstructed. Resume requires the player to already be in the saved dimension. Back up the world and inventory before testing. World blocks, entities, hunger, effects, and other player state are not restored.
- **MIP remains Minecraft-time based:** the scheduled clock crossing (default midnight/tick 18000) is unchanged; it does not necessarily merge after each Nao loop.
- **Equal experiences:** all 24 Nao journals have weight 1.0 in the shared movement decision; there is no special 2x boost for the current Nao. One authoritative `nao_personality.md` remains shared by playable Nao and all 24 NPCs.
- Build and runtime testing are still pending; do not treat the player-state serializer as a complete lossless Minecraft backup.

### Full item-stack metadata preservation

`PlayerStateStore` writes v2 `player.json` inventory slots with Base64-encoded compressed Minecraft ItemStack NBT using the world registry manager. This preserves item data components such as enchantments, damage/durability, custom names and container contents, rather than rebuilding items from ID/count alone. Restore decodes and validates **all** stacks before clearing the current inventory. v1 snapshots remain readable, but any metadata already omitted from a v1 snapshot is unrecoverable. Registry changes, removed modded items, and invalid NBT can prevent restore. **Build and Minecraft runtime validation are pending.**


## Known defects / deferred fixes (2026-11-21)

**Status: unresolved; scheduled for a future maintenance pass.** These are documented risks, not claims that a Fabric 1.21.1 build or in-game test has confirmed every failure. Do not mark them fixed without verifying code, compilation and recovery tests.

1. **Potential Java compile failure in item NBT restore (high priority).** Review `PlayerStateStore.restore()`, especially checked `IOException` handling in the `ItemStack.fromNbt(...).orElseThrow(...)` lambda. Confirm the exact Yarn/Fabric 1.21.1 signatures and compile with Java 21; correct any exception-handling errors.
2. **Non-transactional resume can partially overwrite Markdown (high priority).** `DoppelgangerMod.resume()` copies a committed generation's memory Markdown over the live memory files before all later restore operations succeed. If loading or player restoration fails, memory files may have rolled back even though the session did not resume. Plan: validate and stage all state first, then commit or provide rollback for both files and player state.
3. **Unbounded generation retention (medium priority).** `GenerationCheckpointStore.publish()` retains every complete generation, including 24 Markdown files. Add configurable retention count/size, safe cleanup after a successful publish, and preserve a recoverable previous generation.
4. **No checksum verification for committed generation files (medium priority).** `COMPLETE` plus file existence does not detect corrupted content. Add a manifest with file lengths and SHA-256 digests, verify it before recovery, and support fallback to a prior verified generation.
5. **Incomplete player state snapshot (medium priority).** ItemStack NBT v2 is intended to preserve enchantments, durability, custom names and data components, but hunger, status effects, ender chest, and some other player/world state are not restored. Confirm the intended restoration scope and implement missing fields if full player-state recovery is required.
6. **Legacy v1 snapshots cannot recover missing item metadata (inherent limitation).** Item IDs/counts alone cannot reconstruct enchantments or custom data that were never saved. Keep v1 compatibility but warn before restoring such snapshots.
7. **Working Markdown versus committed generation divergence (recovery policy).** A resume from the last published generation intentionally discards newer uncommitted live Markdown. Document the rollback boundary, optionally back up live files before overwriting, and offer recovery inspection.
8. **Minecraft-clock MIP schedule is not per-loop integration (intentional design).** Keep Minecraft-time scheduling as requested. Do not silently change it to run at each Nao loop; make the distinction explicit in status and docs.

**Future fix acceptance criteria:** Java 21/Fabric 1.21.1 compilation; tests for corrupted/incomplete checkpoints, rollback after restore failures, old v1 and new v2 item stacks, enchanted/damaged/renamed/container items, and generation retention; test with a disposable Minecraft world first. Preserve **one authoritative `nao_personality.md`** for playable Nao and all 24 NPCs, equal weighting of all 24 experiences, optional reward learning default OFF, and Minecraft-time-based MIP scheduling.


## Nao Autonomous Life AI — phased implementation (2026-11-21)

**Current state: initial foundation committed, not a complete autonomous survival NPC; build and in-game tests pending.** The design follows the fictional Nao model: one shared personality and shared knowledge, but independent bodies, goals and learned physical skills.

Implementation order and status:

1. **Goal-based autonomous AI — foundation implemented.** `NaoLifeAI` chooses among EXPLORE, COLLECT, BUILD, REST, SOCIAL and SAFETY using shared MIP evidence, individual energy and skill context. Current stand-ins are armor stands and can only move or remain still; goal names do **not** imply actual mining, crafting, building, social interaction or survival.
2. **MIP index referenced by AI — foundation implemented.** `SharedMemoryReader` reads `nao_integrated_memory.md` and counts validated indexed events by motivation. It is a lightweight heuristic, not semantic understanding; MIP's Minecraft-clock schedule is unchanged. The indexed events are **not** fed into HabitBrain again, avoiding double counting.
3. **Shared memory loading — foundation implemented.** All Nao may consult the same derived MIP knowledge, while each `nao_XX.md` remains independent and `nao_personality.md` remains the single authoritative personality. Knowledge access is equal; individual current circumstances and skill levels can still differ.
4. **Individual context and goals — foundation implemented.** `NaoLifeStore` keeps per-Nao energy, current goal and skill counters. The optional `/doppel life on|off|status` controls the experimental movement/goal layer; **OFF by default**. This is separate from optional reward learning, also OFF by default.
5. **Gradual NPC creation — existing training-loop progression integrated.** One persistent Nao identity is created per recorded training loop, up to 24; earlier Nao stand-ins spawn as training advances. A configurable fictional population-growth schedule (2→4→6→10→16→20→24) is **not** yet implemented.
6. **Individual skill learning — partial.** Exploration can increment from observed stand-in movement; collecting/building/social skills are represented but deliberately **not** increased merely for choosing a goal. Actual activity sensors, world interactions and skill training are future work.
7. **Permanent individual IDs — implemented at application data level.** `config/doppelganger/nao_lives.json` stores UUIDs, goals, energy and skill counters. It is included in generation checkpoints. The armor stand entity UUID itself is not persisted and stand-ins are still recreated; stable NPC entity identity requires a future entity implementation.

**Known limitations / deferred work:** NPCs remain armor stands, not full player-like autonomous entities; no real block-breaking, block placement, pathfinding, inventories or social actions. NPC world positions are not durably saved independently. Goals currently share a simple movement policy, and a shared MIP index is a derived knowledge summary, not a complete synchronized subjective memory. The existing checkpoint/resume atomicity defects and player-state restoration risks documented above remain unresolved. Do not claim the mod is tested or production-ready. Preserve all 24 Nao experience weights equally and do not create per-Nao personality files.


## Documentation consistency audit (2026-11-21)

**The following older statements above are superseded and must not be read as current behavior:**

- Older passages saying that MIP Markdown is *never* consulted by NPC decisions are obsolete when experimental `/doppel life on` is enabled: `SharedMemoryReader` reads the derived `nao_integrated_memory.md` and supplies reason-frequency evidence to `NaoLifeAI.chooseGoal`. This is **not** full semantic memory sharing and does not retrain `HabitBrain`.
- Older passages saying signed item/damage annotations *never* affect actions are obsolete **when** `/doppel reward on` is enabled. With reward learning OFF (the default), the annotations remain non-operative.
- Older passages saying `/doppel resume` never restores inventory or always teleports to the last recorded track are obsolete for a generation with `player.json`: the restore code attempts player position, health, XP and inventory restoration. A legacy recording without a player snapshot follows the old track-position behavior. Neither path rewinds the world.
- The generation manifest description predating life AI omitted `nao_lives.json`. New checkpoints include it, and resume copies it when present. Older generations can lack this optional file.
- The statement that all 24 experiences have identical weights means **each recorded sample has multiplier 1.0**, not that each individual has equal *total* influence irrespective of how many samples they contributed. More prolific journals currently influence the shared movement decision more. If equal influence per Nao is intended, normalize by the number of samples per individual.
- `NaoLifeAI` has goal labels for COLLECT/BUILD/SOCIAL but **no corresponding world actions**. The current skill increment is limited to exploration movement; do not describe it as full skill learning.
- **Unresolved code/design risk:** `DoppelgangerMod.resume()` assigns global `memories` and copies live `nao_lives.json` before player restoration and full session activation. Failure can leave partial live/global state. The identity file is also independent of the checkpoint until publish; interrupted saves/resumes need transactional recovery.
- **Unresolved code/design risk:** a newly started session reuses the existing `nao_lives.json` identities rather than resetting them; define whether `/doppel start` means a fresh cohort or a continuation before changing this behavior.
- **Unresolved behavior:** life AI updates its energy and exploration skill based on the chosen movement before collision/boundary checks; blocked movement can still count as exploration. Update only after successful displacement.
- **Unresolved behavior:** the life AI layer is opt-in but its goal/skill decisions are heuristic, not an LLM or a fully autonomous survival agent. The stable UUID belongs to saved Nao application data, not to a persistent Minecraft entity.
- **Unresolved build/runtime:** no Java 21/Fabric 1.21.1 compilation or in-game test has been completed for these changes. Do not represent implementation as verified.

**Future maintenance:** consolidate superseded sections rather than leaving conflicting historical statements; verify `/doppel start`, `next`, `resume`, `stop`, MIP and both opt-in toggles against actual code, then build and test on a disposable world. Keep one shared `nao_personality.md` and independent `nao_01.md`–`nao_24.md` journals.


### Stage 2 update: actionable shared MIP experience lookup (2026-11-21)

The experimental life AI now reads structured MIP events (source Nao ID, day, tick, position, move, motivation) using `SharedMemoryReader`. Each NPC consults **the same shared read-only index**: for its current goal, it searches for the nearest matching movement experience within 16 blocks and can use that move. If none is found, it falls back to the shared `HabitBrain` policy. `NaoLifeAI.chooseGoal` continues to use integrated motivation counts. Source `nao_XX.md` journals and the sole `nao_personality.md` are not replaced or modified by this lookup; MIP events are not re-trained into HabitBrain. The feature is active only with `/doppel life on` and MIP index data available. MIP still generates the index on the configured Minecraft clock or `/doppel mip run`.

**Limitations / future work:** This is structured event retrieval, not semantic LLM memory or guaranteed safe pathfinding. An event's source Nao ID is retained for traceability but not privileged in retrieval. Nearest-event selection can repeatedly prefer one observation and may choose movement into a wall (existing collision guard prevents entering solid blocks). The 16-block search and matching-goal heuristic are provisional. Missing/stale integrated memory falls back to the existing learned movement model. Build and in-game validation are still pending.
