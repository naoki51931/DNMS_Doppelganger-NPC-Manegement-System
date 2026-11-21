# AGENTS.md — Doppel NPC Management System (DNMS)

## Restart recovery, durable MIP ledger and contextual dialogue (2026-11-21)

- **Auto-resume:** An active session writes `config/doppelganger/session-owner.txt` on checkpoint. After restart the server checks for the saved owner every 40 ticks and calls the existing resume flow when that player is online and in the recorded dimension. Explicit `/doppel stop` clears the marker. `SERVER_STOPPING` attempts a final checkpoint. This is **owner-login recovery**, not unattended NPC simulation while the owner is offline. A corrupt checkpoint, unavailable dimension or failed save can still prevent recovery.
- **Position recovery:** Each living stand-in's coordinates and UUID are saved to `nao_lives.json`; periodic checkpoints are now attempted every 100 server ticks (approximately 5 seconds at 20 TPS), plus clean shutdown. An abrupt crash can still lose up to the most recent successful checkpoint interval; disk failure cannot be guaranteed away.
- **Midnight MIP ledger:** `mip-completed.txt` persists the last successful integration key. Training loops use the active Nao count, while free-play days use a separate day-key range; the catch-up logic skips already completed keys and retries failures. The integrated index is rebuilt atomically from personal journals, with duplicate identical entries removed per Nao. Crash-consistency across the independent marker and generation snapshots is not fully transactional.
- **Dynamic dialogue:** When life AI is enabled, close stand-ins use their current goals, inventory category counts, coordinates, directed relationship state and nearest relevant integrated experience to assemble a two-way Japanese exchange. Dialogue is logged into both personal journals. This is context-conditioned compositional dialogue, **not a language model**; sentence structures remain programmed.
- **Verification:** Consult the latest GitHub Actions run for compilation. Minecraft runtime and fault-injection tests remain outstanding.


## Current implementation status (2026-11-21; supersedes earlier stage tables)

- **Autonomous social interaction:** When `/doppel life on` is enabled, nearby stand-ins (within six blocks) exchange context-selected Japanese dialogue lines every 400 loop ticks. Both directed familiarity values increase, individual SOCIAL skill and TALK evidence are recorded, and `npc_dialogue` events enter the per-Nao journals. Mutual familiarity/trust thresholds enable reciprocal romance opt-in and bounded `ROMANCE_REQUEST` affection growth, recorded as `npc_romance`. This is deterministic, template-based autonomous behavior, **not** open-ended language generation, sophisticated emotional simulation, or an interactive dialogue UI. Existing single shared personality remains unchanged.
- **Daily memory integration:** MIP runs at Minecraft 00:00 (tick 18000). Missed clock crossings, resumes after midnight and manual `/doppel next` use a per-session-day catch-up attempt before the day is reset; failed integrations retry. `/doppel mip run` is still available. Experience weights remain unchanged. This is a best-effort retry mechanism, **not an absolute guarantee during server downtime or storage failures**.
- **Position persistence:** Every session checkpoint now saves each live stand-in's last coordinates alongside its stable Nao UUID in `nao_lives.json`. On `/doppel resume`, stand-ins are reconstructed at saved positions; growing the population keeps existing live stand-ins intact. `/doppel stop` still discards the temporary Minecraft armor stands while preserving their logical identity and last checkpointed coordinates. A crash can lose changes since the most recent successful checkpoint; world chunk availability and spawn errors remain unverified. **Automatic session resume on server boot is not implemented**.
- **Relationship checkpoint consistency:** New generations include `nao_social.json` and restore it on resume when present. Older generations without this file retain legacy behavior. Multi-file restore can still partially fail and is not transactional.
- **Deferred by request:** building protection and target pathfinding remain future work.
- **Verification:** The earlier GitHub Actions build #55 passed on commit `7c347de`. Changes listed in this section require their **own** CI and Minecraft runtime verification; do not treat #55 as verification of newer code.



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


### Stage 3: independent situational planning (2026-11-21)

The opt-in `/doppel life on` NPC layer now has per-Nao goal persistence (`goalUntilTick`), energy, blocked-movement counters, decision counts, and last action/outcome in `nao_lives.json`. `NaoLifeAI.plan` prioritizes unsafe footing/fluid and repeated blocked moves (SAFETY), then low energy (REST), otherwise retains its chosen goal until its deadline before reconsidering shared MIP evidence and individual skills. A shared memory is accessible to all 24, but each stand-in independently evaluates its position and state. Actual movement is checked against the 32-block demonstration radius, destination air and conservative fluid/ground support; exploration skill increases only after observed displacement. The main tick loop's previous malformed nested branch was rewritten.

**Limits and unresolved issues:** SAFETY currently means stop, not finding an escape route; REST restores abstract energy, not Minecraft hunger/health. The safety check is conservative, incomplete and not real pathfinding. COLLECT/BUILD/SOCIAL are goal labels without corresponding world interactions. Action outcomes are lightweight fields, not a detailed timestamped decision journal. Goal deadlines use world tick time; time rewinds or jumps can affect deadlines. The stand-ins are still armor stands and the stage is untested in Java 21/Fabric 1.21.1. Do not claim verified survival or true independent player entities. Earlier statements about skill increases on blocked moves are superseded by the stage 3 tick implementation.


### Optional Nao growth scheduling patch (2026-11-21)

The separate `patches/nao-growth/` source module is included in `build.gradle`. During an active training session, the Minecraft clock triggers **one** additional Nao per day, maximum 24, using the existing `/doppel next` progression and checkpoint logic. There is **no doubling**. Configure with `/doppel growth time HH:mm`; inspect with `/doppel growth status`. The setting persists in `config/doppelganger/growth.time`. Default **06:00 Minecraft time (tick 0, start of the training day)**, not the real-world wall clock. The first Nao is created by `/doppel start`. A clock crossing is required to add the next Nao, so the first automatic addition occurs at the following day's start; a manual `/doppel next` remains available. The feature stops adding individuals once 24 tracks exist or free-play begins.

**Important caveats:** Auto-growth currently calls the existing training transition, which teleports the owner to the training origin, rewinds the Minecraft day to 0, resets observation state, and saves a checkpoint. It does **not** merely spawn a non-disruptive independent NPC. Manual time jumps and server restarts are not backfilled. Auto-growth uses the world time, not a wall-clock timestamp. The trigger and checkpoint integration have not been build-tested or run in Minecraft. The schedule does not change the single shared personality or the independent personal journals.


### Confirmed growth/rewind specification (2026-11-21)

**User-approved behavior (intentional, not a defect):** Each scheduled Nao addition reuses `/doppel next`. The playable **outer Nao** is teleported back to the training origin, the Minecraft clock is reset to 06:00 (world tick 0), and the training observation anchor is reset. This rewind is **deliberate** and MUST NOT be removed as a bug fix without explicit approval. Changes to the world (terrain, blocks, constructions) are not rolled back.

- Growth is strictly **one Nao at a time**, never doubling, up to 24.
- The outer Nao's initial creation is via `/doppel start`; scheduled growth follows at the configured Minecraft time, default 06:00 (start of day).
- Configure with `/doppel growth time HH:mm`; inspect with `/doppel growth status`. The persisted setting is `config/doppelganger/growth.time`.
- Existing Nao identities and journals remain; the newly added Nao gets an individual identity, skills and goals, while the personality and integrated memory are shared.
- Keep the established `/doppel next` checkpoint/save semantics and rewind behavior. Manual `/doppel next` is also allowed.
- Important implementation limitation: scheduling uses Minecraft world time and detects ordinary clock crossings. Clock rewinds/time jumps do not trigger retroactive growth. At the default 06:00, the automatic trigger requires a later crossing of tick 0; it does not immediately fire when `/doppel start` resets time to tick 0.
- Do not describe the rewind as undesirable or propose non-disruptive spawning as the agreed next task. The next planned work is actual individual skill learning and world actions (collect/build), subject to armor-stand limitations and build/runtime verification.


### Stage 5 limited harvesting (2026-11-21)
When `/doppel life on` is active, an NPC with the COLLECT goal attempts to harvest one exposed nearby log every 100 loop ticks (at most 4 blocks from its position, up to 64 stored units, energy >= 20). Only oak, birch, spruce, jungle, acacia, dark oak, mangrove, and cherry logs are eligible. `NaoWorkPatch` directly replaces the log with air and credits one item to that Nao's individual JSON inventory in `nao_lives.json`, increments collection skill, consumes energy, and emits a `npc_collect` memory event. No world item entity is dropped, no player tools, loot tables, block hardness, claims/permissions, natural-tree verification or protected-build detection are implemented. **Important: exposed logs in player buildings are NOT protected.** This is an experimental destructive action; test in a disposable world or backup first. No building or cooperative action has been implemented. Existing one-by-one growth and intentional rewind remain unchanged. Build/runtime testing has not been performed.


### 後日実装（2026-11-21確定）
- 建築物を壊さないための保護判定：天然の原木と建築用の原木の識別、保護区域・権限確認。**未実装、後日実装**。
- 採集対象までの経路探索：障害物を避けて安全に対象へ移動する機能。**未実装、後日実装**。
- 実装再開時は保護判定を先に、その後に経路探索を実装する。
- 現在の採集パッチは建築物の原木も破壊し得るため、保護判定実装まではバックアップ済みテストワールドのみで使用する。


### Stage 7 cooperation baseline (2026-11-21)
`NaoCooperationPatch.java` introduces per-Nao role decisions (`SCOUT`, `GATHER`, `BUILDER`, `REST`) using individual skill and a deterministic starter role. While life AI is enabled, every 200 ticks active stand-ins within 4 blocks may transfer **one log** from a GATHER Nao to a BUILDER Nao. Each participant retains its own inventory; both gain social skill; `npc_transfer` and `npc_receive` events are written to their individual memory journals for later MIP processing. Transfers are limited to 64 inventory units at the recipient and require a sender surplus. The shared plan label `STOCKPILE_LOGS` is conceptual only: no shared task board, autonomous delivery navigation, real building, protection or pathfinding has been implemented. The previously approved one-by-one growth, scheduled reset of the outer Nao and Minecraft time, and deferred structure protection/pathfinding are unchanged. **Not build-tested or run in-game.**


### Stage 8: RLP extension for Nao life goals (2026-11-21)
The existing Reward Learning Patch toggle (`/doppel reward on|off`) now also gates per-Nao goal-reward updates. New `NaoRlpPatch.java` maintains bounded incremental mean reward estimates in `NaoLifeStore.Nao.goalRewards` and `goalAttempts`, persisted in `nao_lives.json` with backwards-compatible initialization. Successful log harvest: COLLECT +5; EXPLORE successful movement +1 or blocked movement -1 (sampled every 20 ticks); successful nearby resource transfer: SOCIAL +3 for both participants. `NaoLifeAI.chooseGoal` uses individual reward estimates in goal scoring, while retaining MIP shared evidence and skills. This is **goal-level online reward estimation, not Q-learning or neural-network training**; BUILD is not rewarded because actual building is not implemented. Existing RLP movement rewards and MIP remain in place. Deferred building protection and pathfinding remain deferred. Code has not been compiled or tested in Minecraft.


## Seven-feature baseline implementation status (2026-11-21)

This is a **partial foundation**, not seven completed Minecraft features. The user requested batching feature work and fixing build errors at milestones rather than after every small commit. The last user-confirmed green GitHub Actions build was commit `7c46db9`; **changes after that are not verified by CI or in-game**.

| Requested feature | Current baseline | Still missing / uncorrected |
| --- | --- | --- |
| 24 visually authentic player NPCs | Stable 24 UUIDs, `skinKey`, appearance commands, vanilla player-head mannequin with limb animation ON by default | **Still armor stands**. Dedicated humanoid entity, Fabric client renderer, actual player skin loading, entity animations and chunk lifecycle missing |
| Mining, building, item collection learning | `NaoActionLearning` confidence model; `NaoLifeStore` persists per-Nao action attempts/successes; actual NPC log collection now records attempts and outcomes | Mining diverse blocks, real construction/placing, craft and inventory fidelity, safe building protection, pathfinding, demonstration capture and real learned action selection missing. Current log harvesting is potentially destructive; back up worlds |
| Conversation, romance, relationships | `NaoRelationshipEngine` directed relationships, mutual romance opt-in guard, `NaoSocialStore` JSON persistence, `/doppel social talk <from> <to>` and `/doppel social status <from> <to>` | Autonomous conversations, language generation, real dialogue UI, autonomous relationship dynamics, explicit romance opt-in command/UI and relationship checkpoint integration missing. Manual talk event is **not** a conversation |
| Full Minecraft world rewind | `NaoWorldRollbackPlan` opt-in policy and bounded journal contract (pure Java, not connected) | **No world rollback**. Block entity NBT, entities, player inventories, chunks, dimensions, other players, durable snapshots, consistency and atomic restore missing. `FULL_WORLD` explicitly rejects activation. Default remains clock + outer player's position |
| Fully control any of 24 | `NaoControlSession` validated selection state machine (not connected); existing `/doppel select` teleports | Input ownership, network packets, entity/player substitution, inventory and camera control, suspending selected AI and resuming owner missing. **No real possession** |
| Advanced decisions directly from integrated memory | Existing `SharedMemoryReader` reads integrated structured events, supports nearest-experience and evidence-based goal scoring in `NaoLifeAI` | Semantic retrieval, planning with actions and outcomes, causal evaluation, robust spatial memory, integrated decision audit and quality evaluation missing |
| Personality growth from experience | `NaoPersonalityGrowth` generates bounded, evidence-backed suggestions after >=10 observations | Not connected to live events or persistence/approval UI. **Never silently rewrite authoritative `nao_personality.md`**; owner review and approval workflow missing |

### Baseline technical details

- `NaoActionLearning`: bounded per-action success counts and Laplace-smoothed confidence; the persisted adapter is `NaoLifeStore.actionAttempts/actionSuccesses`. The collection AI records observed harvest outcomes. No causal proof is inferred from unrelated inventory deltas.
- `NaoSocialStore`: writes `config/doppelganger/nao_social.json` using a temporary file. It is saved during session checkpoints and after manual talk, but **not** included in generation checkpoint restore, so checkpoint rollback consistency is incomplete.
- `NaoControlSession`, `NaoWorldRollbackPlan`, `NaoPersonalityGrowth`: standalone validated core models, **not** Minecraft-integrated behavior.
- Mannequin walking animation is **ON by default**, persists an explicit OFF choice in `config/doppelganger/mannequin-animation.enabled`. Do not flip the default.
- Existing `/doppel select` is a teleport only. Avoid presenting it as NPC possession.
- Never claim build success for these commits without an actual CI result. The newly added core model tests run in `gradle clean build`; batch subsequent compilation fixes.


## Implementation update: region restore, avatar control, MIP planner, owner-mediated growth (2026-11-21)

Historical note: these changes were unverified when this section was written; consult the current implementation status above and GitHub Actions for the latest CI result.

- **Real block restoration (limited scope):** `/doppel region on <radius>` captures the currently loaded blocks in a cube centered on playable Nao, horizontal radius 1..12 and vertical half-height 4. `/doppel next` restores captured block states before advancing the training loop. `/doppel region off` disables it. Snapshot is **in memory only**, not persistent across restarts. It refuses any pre-existing block entity and any unloaded chunk, and refuses restore when a new block entity exists. It does **not** rewind the whole Minecraft world, other players, mobs, inventories, block entities, fluid ticks, or cross-dimension state. On a partial restore failure some blocks may already have changed. Default remains clock + owner position only. Always back up the world before using this opt-in experimental mode.
- **Playable avatar routed to a selected NPC:** `/doppel control select <slot>` in free-play moves the owner's actual player avatar to the selected Nao, pauses that stand-in's AI, and synchronizes its position with the player each tick; `/doppel control release` restores the owner's previous location and releases NPC autonomy. This allows actual keyboard/mouse actions through the **existing player avatar** in the NPC location. It is **not** native possession of a distinct `ServerPlayerEntity`, does not swap inventory/identity/skin, and does not guarantee collisions or full independence. Existing `/doppel select` remains a teleport.
- **Integrated-memory planning:** `NaoGoalPlanner` ranks candidate goals using MIP evidence, per-action outcome statistics, and existing reward preferences, with a primary and fallback goal. `NaoLifeAI.plan` now uses the primary goal; `/doppel plan <slot>` shows the current plan and its evidence. Fallback is informational, not yet executed as a multi-step plan. The planner does not understand natural language or causal outcomes.
- **Personality changes mediated by playable Nao:** `/doppel growth propose <slot>` evaluates observed per-Nao action outcomes (minimum 10 attempts) and **presents** suggested strengths to the playable Nao owner in chat. It **never automatically edits** `nao_personality.md`; proposals are not persisted and no approval/application command is implemented. The player remains the authority for changes.

### Outstanding work

1. Implement durable, consistent, multi-dimension **full-world** snapshots/restore with block entities, entities, inventories, schedules and crash-safe commit. Current implementation is a small block-only cube, not full-world rollback.
2. Replace armor stands with actual player-model entities and support complete avatar/identity/inventory control transfer. Current control is player-avatar movement mirrored to a stand-in.
3. Build a multi-step planner with execution, retry, resource requirements and success/failure feedback. Current MIP planner selects a goal, not a full plan.
4. Persist and present growth proposals with explicit owner approval, versioned personality updates, audit trail, and conflict handling.
5. Verify Fabric 1.21.1 compile, unit tests and in-game behavior for all changes after last known green build `7c46db9`.


### Midnight MIP and persistent Nao stand-ins (2026-11-21)

- **MIP shared-memory integration is fixed to Minecraft 00:00 (tick 18000)**. Older custom `mip.time` values are ignored. `/doppel mip time` accepts only `00:00`. Natural midnight crossing invokes the existing memory integration. Manual `/doppel mip run` and enabling MIP can still trigger an immediate integration; these are explicit/manual exceptions. This does **not** change per-record weights: all experiences retain weight 1.0, so prolific Naos can still contribute more records. A time skip over midnight may skip integration, as before.
- **Nao continuity during growth:** `spawnGhosts()` no longer destroys and recreates every existing stand-in when another Nao is added. Living stands retain their entity and current position; missing/removed stands are recreated for the same slot using their last available position. The logical Nao UUID and saved journals are unchanged. Explicit `/doppel stop` still removes the temporary stand-ins and `/doppel resume` recreates them from saved tracks; **permanent entity persistence, independent position checkpoints, and protection from every kind of disappearance are not yet implemented**.
- Build and in-game verification remain pending.
