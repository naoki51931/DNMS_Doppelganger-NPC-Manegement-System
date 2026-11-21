# Automatic Movement Patch (AMP)

AMP adds opt-in, server-side automatic horizontal movement for the playable Nao in DNMS.

## Commands

```mcfunction
/doppel start
/doppel automove on
/doppel automove status
/doppel automove off
/doppel logs
```

The playable Nao uses the **same shared personality preferences** (`nao_personality.md`) and the available movement-habit models through `HabitBrain.sharedDecision`. The playable Nao's own training model is given double weight. If no movement evidence is available, the character stays still. AMP defaults OFF, resets OFF at the start of each training session, and stops on `/doppel stop`.

The player can still issue movement input, which may compete with the server-applied velocity. Use `/doppel automove off` to regain manual movement. AMP checks the player's destination collision box, support underfoot, ground contact and fluids, but **does not implement pathfinding, guaranteed fall avoidance, combat, building, mining, or complete collision safety**. Test in a safe, flat environment. Every 200 loop ticks it reports its selected direction to `/doppel logs`.

## Technical

`AutomaticMovementPatch.choose` uses shared personality preferences and observed movements. `move` applies server-side horizontal velocity while preserving vertical velocity. The patch is compiled into the mod by `build.gradle`. Requires Fabric 1.21.1 and Java 21. Runtime compatibility has not yet been tested.

### AMP safety update

- **Self-training prevention:** While `/doppel automove on` is active, generated player movement is excluded from `HabitBrain.observe` and the structured movement journal, preventing an automatic movement feedback loop. Other directly observed events (inventory and health) remain available. Manual training resumes after `/doppel automove off`; the previous observation anchor is reset to the player's current position to prevent a false movement sample across the mode switch.
- **Safer movement:** AMP now requires grounded movement, checks the destination player's collision box, verifies support underfoot, and rejects fluid destinations. These are conservative safeguards, **not full pathfinding or guaranteed fall protection**. Avoid testing near cliffs or hazardous terrain until verified.
- Fabric 1.21.1 build and runtime have not yet been validated.

## Signed gameplay observations and shared personality invariant

- Inventory gains are recorded as `event=item_gain detail=score=+N item=...`; observed health loss as `event=damage detail=score=-N health=...`. Inventory losses are recorded as `event=item_loss detail=score=0 count=-N item=...`, because consumption is not necessarily a negative outcome. Scores are **recorded annotations only** and do not yet modify movement preferences or choose actions.
- The Markdown movement parser explicitly ignores `event=` lines lacking `action=`, so reward/damage events cannot become accidental `STAY` training samples.
- AMP mode transitions reset the current Nao brain's observation anchor to avoid training on movement performed by AMP. Manual observations resume after AMP is disabled.
- **Do not split the personality:** playable Nao and all 24 NPC Nao share the same authoritative `nao_personality.md`. Individual journals are experiences, not separate personalities. No change to that invariant is intended.
- Every JSON checkpoint first flushes pending Markdown events. The JSON and Markdown are still separate files and are not a single atomic transaction; a crash between writes can leave a partially advanced Markdown journal. A fully transactional snapshot would require a further versioned manifest/rollback design.
