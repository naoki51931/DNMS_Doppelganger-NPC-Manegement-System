# AMP — developer notes

- Keep automatic movement opt-in, with an immediate `/doppel automove off` escape.
- Reuse the single authoritative `nao_personality.md` and `HabitBrain.sharedDecision`; do not create a separate playable personality.
- Preserve vertical velocity; do not teleport the player each tick.
- Never claim collision checks constitute robust pathfinding or fall protection.
- Avoid learning autonomous AMP outputs as if they were independent human demonstrations in future revisions (feedback-loop risk).
- Add tests for missing training data, blocked positions, and safe shutdown before claiming production readiness.

### AMP safety update

- **Self-training prevention:** While `/doppel automove on` is active, generated player movement is excluded from `HabitBrain.observe` and the structured movement journal, preventing an automatic movement feedback loop. Other directly observed events (inventory and health) remain available. Manual training resumes after `/doppel automove off`.
- **Safer movement:** AMP now requires grounded movement, checks the destination player's collision box, verifies support underfoot, and rejects fluid destinations. These are conservative safeguards, **not full pathfinding or guaranteed fall protection**. Avoid testing near cliffs or hazardous terrain until verified.
- Fabric 1.21.1 build and runtime have not yet been validated.
