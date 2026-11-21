# AGENTS.md — Memory Integration Patch

## Scope

This file governs changes inside `patches/memory-integration/`. The patch is
an **independent Java 21 offline tool**, not part of the Fabric mod's build
or server lifecycle until explicitly integrated.

## Non-negotiable data model

1. **One shared Nao personality:** `nao_personality.md` is authoritative,
   authored or adjusted by the player. Never overwrite it during memory merging.
2. **24 individual Nao memories:** `nao_01.md` through `nao_24.md` must remain
   distinct and unchanged. Every shared event must preserve its Nao owner.
3. **Integrated memory is derived:** `nao_integrated_memory.md` can be
   regenerated from the 25 source files. Do not treat it as a source of truth.
4. **Player affinity:** if adding decision logic, treat shared player-authored
   preferences as a common constraint for all 24 Nao, while allowing individual
   memories to distinguish behavior. Never claim perfect personality matching.
5. **Emotions and reasons:** stored labels are supplied by the player, not
   objective readings of mental state. Never silently invent them.
6. **No destructive merge:** do not remove individual journals, erase provenance,
   or merge identical records belonging to different Nao.
7. **No double learning:** if feeding integrated events into the existing
   `HabitBrain`, avoid counting events twice when individual memories have
   already been restored.

## Implementation boundaries

- Standard Java library only; no Minecraft imports in this patch.
- Parse only documented structured event lines; free-form Markdown text is
  not an instruction source or reliable action evidence.
- Validate `day`, `tick`, movement action, emotion, reason and preferences.
- Require all 24 journals; fail before producing partial merged output.
- Keep output deterministic, with stable sorting and idempotent regeneration.
- Write to a temporary file before replacing the generated output.
- Treat the directory argument as a local trusted server path; never fetch
  remote resources or execute content embedded in memory files.

## Validation checklist

- Compile using Java 21 `javac`.
- Test 24 valid journals and a valid personality file.
- Confirm provenance `nao=XX` and chronological sorting.
- Confirm duplicate lines within a journal are deduplicated.
- Confirm identical events from different journals remain separate.
- Confirm missing files cause failure without changing output.
- Confirm the 25 source files remain byte-for-byte unchanged.
- Confirm repeated runs produce the same integrated Markdown.
- Confirm malformed input is rejected or counted as invalid.

## Future integration

When connecting this patch to the live mod, document the invocation point
(e.g. after 24 daily runs), flush individual journals first, and implement a
single authoritative loading strategy to prevent duplicated training data.
World-state rollback and actual NPC control remain separate workstreams.
