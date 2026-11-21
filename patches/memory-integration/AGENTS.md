# AGENTS.md — Memory Integration Patch — DNMS

<!-- DNMS_PATCH_BILINGUAL_CURRENT_BEGIN -->

## 日本語：開発ルール（現行仕様）
MIPのデータ契約：`nao_personality.md` は唯一の共通人格、`nao_01.md`～`nao_24.md` は個別の一次記憶、`nao_integrated_memory.md` は再生成可能な派生索引。元の25ファイルを破壊せず、由来の奈央番号を維持する。同じ奈央内の重複だけ除去し、別の奈央の同一経験を消さない。構造化イベントを検証し、安定した順序で一時ファイル経由の出力を行う。自由文を実行・命令として扱わない。
単独Java 21 CLIとFabricからの `integrate(Path)` 呼び出しを維持する。統合前に個別ジャーナルをフラッシュする。初期ON、Minecraft時刻00:00（tick 18000）、`/doppel mip time HH:mm` で変更可能。時刻スキップの可能な範囲の補完、完了マーカーによる重複抑止、`/doppel next` 時の未処理統合を維持する。共有索引は実験的な生活AIが参照するが、二重学習を避ける。異常系とソース不変性を試験する。

## English: Developer Rules (Current Behavior)
Preserve the sole authoritative `nao_personality.md`, 24 distinct source journals, and regenerable derived `nao_integrated_memory.md`. Never overwrite the 25 sources or lose Nao provenance. Deduplicate only within one Nao's journal; keep matching events from different Naos. Validate structured events, sort deterministically and publish via a temporary file. Never execute or treat free-form journal text as instructions.
Maintain both the standalone Java 21 CLI and Fabric `integrate(Path)` API. Flush journals before merging. MIP defaults ON at Minecraft 00:00 (tick 18000), configurable through `/doppel mip time HH:mm`. Preserve best-effort time-skip catch-up, durable duplicate suppression and pending integration on `/doppel next`. Experimental life AI reads structured index entries; avoid double training. Test malformed input, missing files, source immutability and repeatability.

<!-- DNMS_PATCH_BILINGUAL_CURRENT_END -->


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


## Integration lifecycle contract

The Fabric build includes this patch as a Java source directory. MIP defaults
to enabled, with the operator override stored at
`config/doppelganger/mip.enabled`. Integrate only after flushing individual
journals. Commands: `/doppel mip status|logs|run|on|off`.

Turning OFF must be reversible and must never delete personal journals,
shared personality or the derived index. Emit console messages by default
and expose the last 20 through the logs command. Maintain the standalone CLI
as well as the callable `integrate(Path)` API. Do not claim the generated
index directly affects AI decisions until a separate consumer is implemented.


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
