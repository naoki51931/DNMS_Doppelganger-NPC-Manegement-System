# AMP — developer notes

<!-- DNMS_PATCH_BILINGUAL_CURRENT_BEGIN -->

## 日本語：開発ルール
AMPは任意機能で初期OFFを維持し、`/doppel automove off` ですぐ停止できること。人格は唯一の `nao_personality.md` を共有し、別のプレイアブル人格を作らない。垂直速度を維持し、毎tickテレポートさせない。AMPが生成した移動を人間の実演として再学習させず、モード切り替え時には観測アンカーをリセットする。衝突・足場判定を完全な経路探索や落下防止と呼ばない。アイテム増加と被ダメージの符号付き記録は注釈であり、自動的な因果学習ではない。JSONチェックポイント前にMarkdownをフラッシュし、複数ファイルの保存が完全な原子的処理ではない点を維持する。欠損データ・障害物・停止時の試験を行う。

## English: Developer Rules
Keep AMP opt-in and OFF by default, with immediate `/doppel automove off`. Reuse the single `nao_personality.md` and never introduce a separate playable personality. Preserve vertical velocity; do not teleport every tick. Exclude AMP-generated movements from human demonstrations and reset observation anchors when switching modes. Do not equate collision/support checks with pathfinding or guaranteed fall protection. Signed item/health observations are annotations, not causal learning. Flush Markdown before JSON checkpoints and document the lack of full cross-file atomicity. Test missing data, blocked positions and shutdown.

<!-- DNMS_PATCH_BILINGUAL_CURRENT_END -->


- Keep automatic movement opt-in, with an immediate `/doppel automove off` escape.
- Reuse the single authoritative `nao_personality.md` and `HabitBrain.sharedDecision`; do not create a separate playable personality.
- Preserve vertical velocity; do not teleport the player each tick.
- Never claim collision checks constitute robust pathfinding or fall protection.
- Avoid learning autonomous AMP outputs as if they were independent human demonstrations in future revisions (feedback-loop risk).
- Add tests for missing training data, blocked positions, and safe shutdown before claiming production readiness.

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
