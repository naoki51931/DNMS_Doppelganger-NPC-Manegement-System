# AGENTS.md — Doppelganger NPC System

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
