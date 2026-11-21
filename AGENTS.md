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
`config/doppelganger/mip.enabled`, and integrates after individual journals
are flushed on `/doppel next` and `/doppel stop` (also at `/doppel start`).
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
