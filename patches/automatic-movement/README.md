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

The player can still issue movement input, which may compete with the server-applied velocity. Use `/doppel automove off` to regain manual movement. AMP checks the next block and head space but **does not implement pathfinding, ledge detection, fall avoidance, combat, building, mining, or complete collision safety**. Test in a safe, flat environment. Every 200 loop ticks it reports its selected direction to `/doppel logs`.

## Technical

`AutomaticMovementPatch.choose` uses shared personality preferences and observed movements. `move` applies server-side horizontal velocity while preserving vertical velocity. The patch is compiled into the mod by `build.gradle`. Requires Fabric 1.21.1 and Java 21. Runtime compatibility has not yet been tested.

### AMP safety update

- **Self-training prevention:** While `/doppel automove on` is active, generated player movement is excluded from `HabitBrain.observe` and the structured movement journal, preventing an automatic movement feedback loop. Other directly observed events (inventory and health) remain available. Manual training resumes after `/doppel automove off`.
- **Safer movement:** AMP now requires grounded movement, checks the destination player's collision box, verifies support underfoot, and rejects fluid destinations. These are conservative safeguards, **not full pathfinding or guaranteed fall protection**. Avoid testing near cliffs or hazardous terrain until verified.
- Fabric 1.21.1 build and runtime have not yet been validated.
