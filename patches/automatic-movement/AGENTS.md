# AMP — developer notes

- Keep automatic movement opt-in, with an immediate `/doppel automove off` escape.
- Reuse the single authoritative `nao_personality.md` and `HabitBrain.sharedDecision`; do not create a separate playable personality.
- Preserve vertical velocity; do not teleport the player each tick.
- Never claim collision checks constitute robust pathfinding or fall protection.
- Avoid learning autonomous AMP outputs as if they were independent human demonstrations in future revisions (feedback-loop risk).
- Add tests for missing training data, blocked positions, and safe shutdown before claiming production readiness.
