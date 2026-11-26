# Doppel NPC Management System (DNMS)

## 日本語：記憶想起・人物台帳・自律計画（2026-11-21）
- `/doppel recall <slot> <topic>` は各奈央の個別記憶ファイル（MIP複製済みの他者エピソードを含む）をキーワード検索し、出典付きの一致行を返す。生活AIは長期目標をキーワードとして定期的に記憶を想起し、`lastRecalledMemory` に保持する。**これはルールベースのエピソード検索であり、LLMによる自由な意味理解や会話生成ではない。**
- `/doppel person add <id>`、`person relate <from> <to> <role>`、`person event <from> <to> <detail>`、`person status <id>`。外部人物と奈央（`nao_1`〜`nao_256`）の人物台帳を `config/doppelganger/people.json` に保存する。関係ラベル：FRIEND, FAMILY, COLLEAGUE, PARTNER, SPOUSE, PARENT, CHILD, HOUSEMATE。相互関係と親子の逆関係を自動登録し、共有イベントを両者へ記録する。結婚式・育児・同居の物理行動は未実装。
- `/doppel plan <slot>` は長期目標・職業を優先する段階的プラン（OBSERVE→PREPARE→PRACTICE→REVIEW）を表示する。生活AI実行中は定期的に目標に沿った行動カテゴリを選ぶ。**目標達成の意味評価や多段階の実作業は未実装。**
- 25人以上で操作できなかったプレイアブル選択の24人固定条件を修正。従来の24回の学習ループ完了条件は意図的に24のまま。
- 現状の人物台帳は独立JSON保存で、世代チェックポイントの原子的な一括復元には未統合。ゲーム実機と大規模256人負荷テストは未実施。

## English: Episodic Recall, People Registry, Autonomous Planning (2026-11-21)
- `/doppel recall <slot> <topic>` searches each Nao's personal journal including physically replicated MIP episodes, returning a matching attributed line. Life AI periodically recalls using its long-term goal and persists `lastRecalledMemory`. **This is keyword-based recall, not LLM semantic understanding or generative dialogue.**
- `/doppel person add <id>`, `person relate <from> <to> <role>`, `person event <from> <to> <detail>`, `person status <id>` persist external people and Nao identities (`nao_1` through `nao_256`) in `config/doppelganger/people.json`. Roles: FRIEND, FAMILY, COLLEAGUE, PARTNER, SPOUSE, PARENT, CHILD, HOUSEMATE. Symmetric and parent-child inverse roles and shared event histories are persisted. Physical cohabitation, marriage ceremonies and childcare remain unimplemented.
- `/doppel plan <slot>` reports a personal ambition-driven staged plan (OBSERVE→PREPARE→PRACTICE→REVIEW). Life AI periodically selects action categories based on long-term goals. **Complex task execution and true achievement verification remain future work.**
- Fixed the 24-only guard for playable Nao selection after adding more identities. The original 24-loop training-completion condition remains intentionally unchanged.
- The new people registry is saved separately and is not yet part of an atomic generation checkpoint. Minecraft runtime and large 256-Nao load tests remain outstanding.


## 日本語：256人と全記憶複製（現行実装）
- 最大人数は**256人**（`/doppel dashboard max 256`、設定ファイル `config/doppelganger/max-naos.txt`）。**初期生成は24人**（`/doppel dashboard bootstrap`）で、フリープレイ中に `/doppel dashboard add <人数>` で追加する。24人の手動訓練は従来どおり。
- 人数上限を256に拡張した対象：個別UUID・生活データ、プレイアブル選択、スロット引数、記録スナップショット、外見登録、関係保存、個別記憶、世代チェックポイント、MIPの統合対象。
- MIP統合の際、**他の奈央の元エピソード行を各奈央の個別Markdownへ物理複製**する。`shared_from_nao=<番号>` を付けて出典を保存し、元の `- day=` 行と区別する。各ファイルの `DNMS_SHARED_COPY_BEGIN/END` 範囲を置換して再同期時のコピー重複を防ぐ。本人の新しい記憶は保持する。共通索引 `nao_integrated_memory.md` は引き続き参照可能。
- 注意：256人への完全複製は**記憶件数×人数**のディスク容量を消費し、世代チェックポイントの容量も増える。大量データでの負荷試験、実機テスト、異常終了時の複数ファイル一括整合性保証は未完了。複製済みエピソードを本人の身体技能として再学習しない。

## English: 256 Naos and Full Memory Replication (Current Implementation)
- The maximum population is **256** (`/doppel dashboard max 256`, persisted in `config/doppelganger/max-naos.txt`). **Bootstrap still creates 24** (`/doppel dashboard bootstrap`); use `/doppel dashboard add <count>` during free-play to add more. The original 24-loop manual training path remains.
- Expanded 256-slot support includes stable identities, life profiles, playable selection, command slots, snapshots, appearance metadata, social persistence, personal journals, generation checkpoints and MIP integration.
- Each MIP merge now **physically copies the original episodic lines of other Naos into each Nao's Markdown journal**, tagged `shared_from_nao=<source>`. Shared entries are separated from personal `- day=` training events by a bounded `DNMS_SHARED_COPY_BEGIN/END` section that is replaced on subsequent merges, avoiding duplicate copies. Newly recorded personal experiences are retained. The shared `nao_integrated_memory.md` index remains readable.
- Caution: 256-way full copying scales disk usage with **population × memory volume**, also increasing generation-checkpoint size. Large-load tests, in-game validation and fully atomic cross-journal synchronization are outstanding. Replicated episodes are not replayed as the recipient's own physical skill training.


## 日本語：外側の管理UI・生活プロファイル（2026-11-21）

- **管理UI（サーバー専用）:** `/doppel dashboard` はMinecraftチャットにクリック可能な「DOPPELGÄNGER UNLIMITED」管理画面を表示します。クライアント専用GUIやWeb画面ではありません。現在の人数、上限、状態、NPCごとの職業・住居・長期目標を表示し、追加・初期生成・詳細表示へ移動できます。
- **初期生成:** `/doppel dashboard bootstrap` で、オーナーの現在位置を基点にデフォルト24人の奈央をフリープレイとして初期生成します。従来の24回訓練とは別の簡易初期化経路です。AIの習熟・人生経験が24人分自動生成されるわけではありません。
- **人数上限:** `/doppel dashboard max <1..24>` で最大人数を設定し、`config/doppelganger/max-naos.txt` に保存します。デフォルトは24人。既存コードの固定24人前提（記憶ファイル、スロット、UI、セーブ）により、25人以上は**未対応**です。将来の設計目標として256人は検討できますが、性能・記憶・チェックポイント形式の改修と負荷試験が必要です。
- **追加:** `/doppel dashboard add <1..24>` はフリープレイ中、上限の範囲内で新しい奈央を追加します。削除コマンドは設けていません。
- **生活プロファイル:** `/doppel dashboard job <slot> <name>`、`home <slot> <name>`、`goal <slot> <name>` で職業・住居名・長期目標を保存します。`profile <slot>` で確認します。1日のルーチンと夕方の関係イベントを個別の記憶に記録します。**現段階では実際の通勤経路探索、仕事、家事、育児、帰宅移動を実行するAIではありません。**
- **個別関係:** `/doppel dashboard relation <from> <to> <FRIEND|FAMILY|COLLEAGUE|PARTNER|SPOUSE|PARENT|CHILD>` で方向付きの関係ラベルを保存します。家族・結婚・育児の実世界シミュレーションは未実装です。
- **記憶同期の設計課題:** 現在のMIPは共有索引の生成・参照であり、24人全員に同じエピソード記憶を物理的に複製してはいません。小説の「全員が他の23人の経験を自分の記憶として想起する」を実現するには、イベントID・出典UUID・発生時刻・同期世代を持つ共通の不変イベントログと、各奈央の既読カーソル／由来付き検索を実装する必要があります。二重学習・重複配信・保存途中の障害への対策が必要です。

## English: Outer-World Dashboard and Life Profiles (2026-11-21)

- **Server-only interactive UI:** `/doppel dashboard` opens a clickable Minecraft chat dashboard labeled “DOPPELGÄNGER UNLIMITED.” It is **not** a custom client screen or web dashboard. It displays population, cap, session state and each Nao's job, home and long-term goal.
- **Default population:** `/doppel dashboard bootstrap` initializes a default of 24 Nao identities in free-play at the owner's location. This is an alternative to 24 manually trained loops and does not fabricate 24 distinct trained histories.
- **Maximum:** `/doppel dashboard max <1..24>` persists the population cap in `config/doppelganger/max-naos.txt`. Default is 24. **More than 24 is not supported** by the existing fixed-slot memory, save and command architecture. A theoretical future target of 256 requires refactoring and load tests.
- **Add identities:** `/doppel dashboard add <1..24>` adds identities in free-play without exceeding the cap. There is no delete command.
- **Life profiles:** `job`, `home`, `goal`, and `profile` subcommands manage persisted per-Nao job, residence label and long-term goal. A daily routine and evening relationship check-in are recorded in individual journals. **Actual commuting, jobs, household activities, childcare and homeward navigation are not yet simulated.**
- **Directed relationships:** `/doppel dashboard relation <from> <to> <role>` persists FRIEND, FAMILY, COLLEAGUE, PARTNER, SPOUSE, PARENT or CHILD roles. This is not a full marriage, family or childcare simulation.
- **Memory synchronization remains a design task:** MIP currently builds and reads a shared index; it does not replicate every episode into every Nao's personal memory. Full novel-style recall needs immutable attributed event IDs, origin UUIDs, event timestamps, synchronization generations and per-Nao cursors, with deduplication and crash-safe checkpoints.


<!-- DNMS_BILINGUAL_GUIDE_BEGIN -->

## 日本語ガイド / Japanese Guide (現行仕様)

### 概要
DNMSはMinecraft Java Edition **Fabric 1.21.1**向けの実験的なサーバーMODです。プレイヤーの経験を24人の「奈央」に記録し、個別の記憶・UUID・行動目標・簡易インベントリ・人間関係を維持しながら、共通人格とMIP統合記憶を参照するNPC生活を目指します。NPCの外見は現在、プレイヤー型エンティティではなく防具立てを使用しています。

### 導入と基本操作
Java 21とGradle 8.xで `gradle build` を実行し、生成JARとFabric APIをFabric 1.21.1サーバーに導入します。管理者権限で `/doppel start` を実行し、行動を記録します。`/doppel next` で次の奈央に進み、24人が揃うとフリープレイに移行します。`/doppel growth time HH:mm` で訓練中の追加時刻を設定できます（初期値06:00）。`/doppel status` で進捗を確認します。

### 24人の生活とプレイアブル選択
24人完成後、`/doppel day next` で次の日の06:00へ進みます。NPCのAI・位置・関係性と世界の出来事は維持されます。`/doppel control select <1..24>` で任意の奈央をプレイアブルとして選択し、選択中の奈央の自律移動だけを停止します。`/doppel control release` で解除します。`/doppel select <1..24>` は位置への移動のみで、プレイアブル切り替えではありません。現在はプレイヤーアバターを移動させる簡易方式で、独立した身体や完全なインベントリ交換は未実装です。

### 再起動後の無人NPC生活と途中合流
`/doppel life on` と `/doppel offline on` を有効化し、24人のフリープレイを `/doppel save` で保存します。サーバー再起動後は、保存済みオーナーのログインを待たずにNPCセッションを復元する処理があり、NPCは稼働中のサーバーtickに沿って活動します。オーナーが後からログインしても稼働中のNPC生活を再ロードせず、`/doppel control select <slot>` で合流します。サーバー停止中の実時間は早送りしません。復元には正常なチェックポイント、保存済みプレイヤー状態・ディメンション、セッションオーナー情報が必要です。**Minecraft実機での無人再起動・長時間稼働は未検証**です。

### 記憶・AI・保存
個別記憶は `config/doppelganger/memories/nao_01.md` ～ `nao_24.md`、共通人格は `nao_personality.md`、MIP統合記憶は `nao_integrated_memory.md` です。`/doppel mip time HH:mm` でMinecraft時刻による統合タイミングを設定できます（初期値00:00）。`/doppel mip run` で手動実行できます。NPCは限定的な原木採集、アイテム受け渡し、状況依存の定型会話、関係値に基づく恋愛進展を行えます。約100サーバーtickごとにチェックポイントを試行します。

### 制約と注意
原木採集はプレイヤーの建築物も壊す可能性があります。**バックアップ済みテストワールドを使用してください。** 完全な経路探索、建築保護、自律建築、LLM会話、高度な恋愛上の自由意思、完全な世界巻き戻しは未実装です。世界の巻き戻しは原則行わず、オプションの `/doppel region on <radius>` のみ限定的なブロック復元を行います。GitHub Actionsのビルド成功はMinecraft実機での動作保証ではありません。

## English Guide / 英語ガイド (Current Behavior)

### Overview
DNMS is an experimental **Fabric 1.21.1** server mod for Minecraft Java Edition. It records one player's experiences across up to 24 Nao identities, each with separate memories, UUID, goals, lightweight inventory, and relationships. They share one personality profile and an integrated MIP memory index. NPCs currently use armor-stand stand-ins rather than real player entities.

### Installation and training
Build with Java 21 and Gradle 8.x using `gradle build`; install the generated JAR and Fabric API on a compatible server. As an operator, run `/doppel start` to begin recording and `/doppel next` to advance to the next Nao. Free-play begins after all 24 are recorded. Set automatic training growth time with `/doppel growth time HH:mm` (default 06:00); check progress using `/doppel status`.

### Free-play and playable Nao
After all 24 identities exist, `/doppel day next` advances to the next Minecraft day at 06:00 without resetting NPC brains, positions, relationships, or world changes. `/doppel control select <1..24>` routes the existing player avatar to a selected Nao and pauses only that stand-in's autonomous movement. `/doppel control release` releases control. `/doppel select <1..24>` only teleports to a stand-in's position. Full entity possession and inventory swapping are not implemented.

### Unattended restart and later player join
Enable `/doppel life on` and `/doppel offline on`, then save the completed 24-Nao free-play with `/doppel save`. On server restart, the mod attempts to restore the NPC session without waiting for the owner to log in, using the saved checkpoint, owner identity, player snapshot, and dimension. NPC simulation advances while the server runs; it does **not** fast-forward through server downtime. A returning owner joins the already-running world without resetting NPC progress and can select a playable Nao. **Unattended restart and extended runtime behavior have not been validated in Minecraft.**

### Memory, AI, and persistence
Individual journals live at `config/doppelganger/memories/nao_01.md` through `nao_24.md`; `nao_personality.md` is shared, and `nao_integrated_memory.md` is the derived MIP index. Configure MIP with `/doppel mip time HH:mm` (default 00:00 Minecraft time), or run it manually with `/doppel mip run`. Limited NPC features include nearby exposed-log harvesting, item transfers, contextual template dialogue, and relationship-threshold romance. Checkpoints are attempted approximately every 100 server ticks.

### Limitations and safety
NPC log harvesting may destroy logs in player-built structures: **use backed-up test worlds**. Full pathfinding, building protection, autonomous construction, open-ended LLM dialogue, sophisticated romantic autonomy, and full-world rewind are not implemented. `/doppel region on <radius>` offers only an optional bounded block-state restore. A successful CI build does not establish in-game correctness.

<!-- DNMS_BILINGUAL_GUIDE_END -->


## 無人再起動復元とプレイアブル奈央の途中合流（2026-11-21）

24人の訓練を完了したフリープレイで `/doppel life on` と `/doppel offline on` を有効にし、セッションをチェックポイント保存しておくと、サーバー再起動後はオーナーのログインを待たずに、保存済みのディメンション・24人の個別記憶・位置・関係性・AI状態を復元し、NPCシミュレーションを再開します。復元には `session-owner.txt`、最新の正常な世代チェックポイントと保存済み `player.json` が必要です。失敗時は1200サーバーtick間隔で再試行します。

オーナーが後からログインした場合、稼働中のNPCセッションを**再ロードせず**、進行した世界とNPC状態を維持します。`/doppel control select <1..24>` で任意の奈央に途中合流できます。選択したNPCの自律移動だけを止め、残りのNPCは活動を継続します。プレイヤーの移動経験は選択した奈央の個別記憶へ記録します。サーバー停止中の経過時間は早送りしません。再起動後のサーバーtickで進行します。

**制約:** Minecraft実機での再起動・チャンクロード・長時間無人稼働・クラッシュ耐性は未検証です。NPCは防具立ての代替エンティティで、プレイアブル操作は既存プレイヤーアバターの位置切り替えです。オーナー不在の訓練中ループは自動復元しません。旧記述の「無人コールドブート復元は未実装」はこの節により更新されます。


## Post-24 free-play day advance and playable Nao (2026-11-21)

After all **24 Nao identities** have been trained and `freePlay` is active, the external day-advance command `/doppel day next` starts the following Minecraft day at 06:00 (world time tick 0) **without recreating NPCs, resetting their AI, restoring blocks, or erasing world events**. It checkpoints before changing days, keeps each NPC's goal/brain, position and relationship state, and saves the new day. The legacy `/doppel next` remains supported in free-play.

Use `/doppel control select <1..24>` to make a particular NPC the **playable Nao**. The player's avatar teleports to that Nao's current position, the selected NPC stand-in pauses its autonomous movement, and the other Nao NPCs continue their AI. The player's experience while controlling that Nao is recorded to that Nao's personal journal as `playable_experience` every 20 ticks; their position is persisted. `/doppel control release` returns to the owner avatar. `/doppel select <slot>` is only a teleport/view selection, **not** playable-identity assignment. This is an avatar-routing MVP, not independent player-entity possession or full inventory swapping. All 24 must exist before the new day-advance command is enabled.


## Authoritative current implementation and limitations (2026-11-21)

- **Configurable MIP:** `/doppel mip time HH:mm` accepts 00:00–23:59, persists `mip.time`, and uses the Minecraft clock (default 00:00 / tick 18000). Skipped crossings are caught up where possible, with a durable `mip-completed.txt` integration key to suppress repeats. `/doppel next` also attempts pending integration before advancing; the journal merge is not a fully transactional guarantee.
- **Checkpoints:** Approximately every **100 server ticks** (5 seconds at 20 TPS), plus manual saves and clean shutdown. Positions and stable Nao UUIDs are persisted in `nao_lives.json`; new generations include `nao_social.json`. Recovery restores the last successful checkpoint, not necessarily the crash-instant position. Generation retention is currently unbounded.
- **Auto-resume:** After restart, the saved owner automatically resumes on login in the saved dimension. **Owner-offline mode**: `/doppel offline on|off|status` persists an opt-in setting; with an already active/resumed session and `/doppel life on`, NPCs continue AI movement, limited gathering, cooperation and contextual dialogue while the owner is absent. Offline checkpoints use the last saved player state; **unattended cold-boot session restoration is not yet implemented**. Owner training and player movement do not continue while absent.
- **NPCs:** Still armor-stand stand-ins, not authentic player entities. Life AI has individual lightweight inventory counts, limited exposed-log harvesting, cooperation, directed social relationships, autonomous two-way contextual Japanese dialogue and gradual reciprocal romance. Dialogue combines goal, local position, item-category counts, shared indexed experiences and relationship values; it is not LLM text generation. Rich compatibility, rejection, and individual agency in romance are **future development goals**, not current features.
- **Shared memory:** MIP builds a common derived index from per-Nao journals and one shared personality; experimental life AI consults the index. Equal per-observation weights remain unchanged.
- **World restore:** Default training transition resets Minecraft clock and playable Nao position, not world events. Optional `/doppel region on <radius>` supports a limited block-only region restore, not full world/entity/inventory rewind.
- **Selection:** `/doppel select <slot>` moves the owner to the selected stand-in's current position in free-play (AI-driven or replay-driven). `/doppel control select <slot>` is limited avatar routing and pauses that stand-in; neither is full NPC possession.
- **Implemented vs deferred:** Goal selection, limited collect/social actions, MIP reading, position persistence, auto-resume on owner login and limited control routing are implemented. Full humanoid NPCs, pathfinding, building protection, autonomous building, full-world rollback, multi-step planning, personality proposal approval and richer romantic autonomy remain future work.
- **Verification:** GitHub Actions Fabric build passed according to the latest user confirmation. Minecraft runtime, crash-injection and unattended-mode tests remain outstanding. Historical milestone sections below are retained for context; any conflicting earlier claim is superseded by this section.


## Restart recovery, durable MIP ledger and contextual dialogue (2026-11-21)

- **Auto-resume:** An active session writes `config/doppelganger/session-owner.txt` on checkpoint. After restart the server checks for the saved owner every 40 ticks and calls the existing resume flow when that player is online and in the recorded dimension. Explicit `/doppel stop` clears the marker. `SERVER_STOPPING` attempts a final checkpoint. This is **owner-login recovery**, not unattended NPC simulation while the owner is offline. A corrupt checkpoint, unavailable dimension or failed save can still prevent recovery.
- **Position recovery:** Each living stand-in's coordinates and UUID are saved to `nao_lives.json`; periodic checkpoints are now attempted every 100 server ticks (approximately 5 seconds at 20 TPS), plus clean shutdown. An abrupt crash can still lose up to the most recent successful checkpoint interval; disk failure cannot be guaranteed away.
- **Midnight MIP ledger:** `mip-completed.txt` persists the last successful integration key. Training loops use the active Nao count, while free-play days use a separate day-key range; the catch-up logic skips already completed keys and retries failures. The integrated index is rebuilt atomically from personal journals, with duplicate identical entries removed per Nao. Crash-consistency across the independent marker and generation snapshots is not fully transactional.
- **Dynamic dialogue:** When life AI is enabled, close stand-ins use their current goals, inventory category counts, coordinates, directed relationship state and nearest relevant integrated experience to assemble a two-way Japanese exchange. Dialogue is logged into both personal journals. This is context-conditioned compositional dialogue, **not a language model**; sentence structures remain programmed.
- **Verification:** Consult the latest GitHub Actions run for compilation. Minecraft runtime and fault-injection tests remain outstanding.


## Current implementation status (2026-11-21; supersedes earlier stage tables)

- **Autonomous social interaction:** When `/doppel life on` is enabled, nearby stand-ins (within six blocks) exchange context-selected Japanese dialogue lines every 400 loop ticks. Both directed familiarity values increase, individual SOCIAL skill and TALK evidence are recorded, and `npc_dialogue` events enter the per-Nao journals. Mutual familiarity/trust thresholds enable reciprocal romance opt-in and bounded `ROMANCE_REQUEST` affection growth, recorded as `npc_romance`. This is deterministic, template-based autonomous behavior, **not** open-ended language generation, sophisticated emotional simulation, or an interactive dialogue UI. Existing single shared personality remains unchanged.
- **Daily memory integration:** MIP runs at Minecraft 00:00 (tick 18000). Missed clock crossings, resumes after midnight and manual `/doppel next` use a per-session-day catch-up attempt before the day is reset; failed integrations retry. `/doppel mip run` is still available. Experience weights remain unchanged. This is a best-effort retry mechanism, **not an absolute guarantee during server downtime or storage failures**.
- **Position persistence:** Every session checkpoint now saves each live stand-in's last coordinates alongside its stable Nao UUID in `nao_lives.json`. On `/doppel resume`, stand-ins are reconstructed at saved positions; growing the population keeps existing live stand-ins intact. `/doppel stop` still discards the temporary Minecraft armor stands while preserving their logical identity and last checkpointed coordinates. A crash can lose changes since the most recent successful checkpoint; world chunk availability and spawn errors remain unverified. **Owner-login auto-resume is implemented; unattended cold-boot restoration is not yet implemented**.
- **Relationship checkpoint consistency:** New generations include `nao_social.json` and restore it on resume when present. Older generations without this file retain legacy behavior. Multi-file restore can still partially fail and is not transactional.
- **Deferred by request:** building protection and target pathfinding remain future work.
- **Verification:** The earlier GitHub Actions build #55 passed on commit `7c347de`. Changes listed in this section require their **own** CI and Minecraft runtime verification; do not treat #55 as verification of newer code.



**DNMS** (formerly Doppelganger NPC System) is an experimental Minecraft Java Edition **Fabric 1.21.1** server mod plus C++ console prototypes for a 24-character daily loop.

## Fabric mod (early MVP)

The Java mod records one player's positions across repeated simulated days and uses per-character movement habit models to move armor-stand NPC stand-ins autonomously (or replays recorded tracks). This is **not yet a complete Minecraft NPC or rollback implementation**.

### Build

Requires Java 21 and Gradle 8.x (no Gradle wrapper is included yet).

```bash
gradle build
```

Copy the generated `build/libs/doppelganger-npc-system-0.1.0.jar` into a compatible Fabric 1.21.1 server's `mods/` folder with Fabric API. GitHub Actions Fabric build has passed; Minecraft runtime compatibility remains to be tested.

### Commands (operator permission required)

- `/doppel start` — begin day 1 loop 1 and capture the player's position every server tick
- `/doppel next` — finish the current recording, reset the clock and player position, and begin the next loop
- `/doppel status` — show progress
- `/doppel select 1` through `/doppel select 24` — after 24 recordings, teleport the player to the selected recorded ghost's current replay position
- `/doppel emotion curious` — during training, label the player's current self-reported emotion; choices: `neutral`, `happy`, `curious`, `afraid`, `angry`, `sad`
- `/doppel reason explore` — during training, label the player's current self-reported reason; choices: `none`, `explore`, `build`, `collect`, `safety`, `social`
- `/doppel preference explore 9` — set shared Nao personality affinity for a reason from 0 to 10 (also: `none`, `build`, `collect`, `safety`, `social`)
- `/doppel ai on` — use learned movement habits for autonomous NPC movement (default)
- `/doppel ai off` — switch to exact recorded-position replay
- `/doppel stop` — save recordings to JSON and stop the session (does not discard recordings)
- `/doppel save` — explicitly save the active session to JSON
- `/doppel resume` — restore a saved session from JSON

The recording window is at most 24,000 ticks (one standard Minecraft day) per character. `next` can be invoked before a full day is recorded; for complete daily tracks, wait until the day ends.

### Important limitations

- **No full rewind of the world (optional bounded block-only region restore exists)**: block edits, mobs, weather, player inventory and other game state are NOT restored. By default only the clock and player's position reset; opt-in bounded block-only restore is available.
- NPC stand-ins are armor stands driven by learned directional frequencies conditioned on day/night and the player's explicitly labeled emotion and reason. They are not full player models, pathfinding agents, or language-model AI.
- Emotions and reasons are player-supplied annotations, not detected mental states. The last supplied labels persist for the rest of that character's training and later AI behavior. The model does not independently infer changing emotions, nor does it guarantee perfect preference matching.
- The base habit policy learns movement direction; opt-in life AI additionally learns limited action outcomes. The reason label `build` does not teach actual building, and `collect` labels alone do not teach gathering, but life AI can harvest exposed logs.
- The movement-only policy itself lacks full mining/building/pathfinding, while the opt-in life AI adds a small inventory model, limited log harvesting, cooperation, dialogue and romance.
- All 24 NPCs use **one shared personality profile** as an affinity weighting system, plus the combined evidence of all characters' demonstrations. All 24 characters' experiences are weighted equally when selecting an action. This is a heuristic, not full psychological or semantic understanding.
- `/doppel preference` is the player's explicit preference, not an automatically inferred psychological trait. Emotions and reasons are likewise explicitly labeled.
- The Markdown files are a readable structured journal, **not** an LLM memory retrieval system. Only structured movement samples are parsed for decisions. Free-form notes are not yet interpreted.
- Recorded movement observations are sampled every 20 ticks (approximately once per second); only samples flushed to disk are durable. The stand-ins are not real controllable player entities.
- `/doppel select <slot>` teleports the owner to the selected stand-in's current position in free-play; `/doppel control select <slot>` additionally pauses the selected stand-in's AI and mirrors the playable avatar position, without full possession.
- Recorded position tracks are held in memory while active and are persisted in `config/doppelganger/recordings.json` on `/doppel stop`, `/doppel next`, `/doppel save`, and periodic checkpoints (every 100 server ticks). Abrupt crashes may still lose changes since the last successful save. However, sampled movement observations and their player-labeled emotions/reasons are appended to individual Markdown journals on `/doppel next` and `/doppel stop`, and are reloaded into the learner on `/doppel start`. A crash before flushing may lose recent samples.
- Multiplayer, dimension changes, restarts, collisions, death, and chunk loading are not yet robustly handled.
- Basic movement imitation is integrated in the Fabric mod; the richer C++ learner remains a separate demo.

## C++ console prototypes

`src/doppel_integrated.cpp`: 24 rounds of simplified habit learning and free switching.

`src/doppel_day_loop.cpp`: deterministic same-day recording and merge simulation.

```bash
g++ -std=c++17 -O2 src/doppel_integrated.cpp -o doppel
./doppel
g++ -std=c++17 -O2 src/doppel_day_loop.cpp -o dayloop
./dayloop 30
```

Other demos: `src/doppel_npc.cpp`, `src/doppel_switch.cpp`.

## Next development milestones

1. Build/test the Fabric mod and correct any API mismatches.
2. Implement reliable bounded-region world snapshots and rollback (including block entities), with safety limits.
3. Record and replay meaningful actions (movement, mining, placing, interactions), not only positions.
4. Replace armor stands with controllable NPCs and integrate learned habit policies.
5. Persist recordings and daily state; reconcile conflicting actions when 24 timelines merge.
6. Support switching control among all 24 NPCs while the other 23 continue autonomously.

## Shared personality and individual Markdown memory

The Fabric mod creates the following files on the **server** at `config/doppelganger/memories/` (not inside this public GitHub repository):

```text
config/doppelganger/memories/
  nao_personality.md   # one personality shared by all 24
  nao_01.md            # Nao 01 personal observations
  nao_02.md            # Nao 02 personal observations
  ...
  nao_24.md            # Nao 24 personal observations
```

Example in-game training:

```mcfunction
/doppel start
/doppel preference explore 9
/doppel preference safety 8
/doppel emotion curious
/doppel reason explore
```

Move around to teach Nao 01, then type `/doppel next` to flush her memory and move to Nao 02. Repeat through Nao 24. Each character's Markdown journal is separate, but their action policy references the same `nao_personality.md`. The decision score combines shared demonstrations, preference affinity and equal weight for all 24 Nao experiences.

**Privacy:** journals contain recorded in-game positions and player-supplied emotional labels. Keep the server files private if you do not want to publish them.

## Optional patch: Memory Integration

The standalone **[Memory Integration Patch](patches/memory-integration/README.md)**
lives under `patches/memory-integration/`, with implementation guidance in
`patches/memory-integration/AGENTS.md`.

It reads the 24 individual `nao_XX.md` journals and the authoritative
`nao_personality.md`, then generates an attributed shared index
`nao_integrated_memory.md`. The source journals and shared personality are
not overwritten. The patch can run as a **standalone Java 21 tool** and is also compiled into the Fabric mod. Experimental life AI consults its structured shared-memory index.

From the repository root:

```bash
javac -d patches/memory-integration/out patches/memory-integration/src/org/nagineco/patch/memoryintegration/MemoryIntegrationPatch.java
java -cp patches/memory-integration/out org.nagineco.patch.memoryintegration.MemoryIntegrationPatch config/doppelganger/memories
```


### MIP automatic mode (default ON)

The Memory Integration Patch is compiled into the Fabric mod by `build.gradle`.
MIP generates `nao_integrated_memory.md` on scheduled in-game time crossings (default 00:00), on `/doppel mip run`, and when MIP is enabled with `/doppel mip on` while a memory store exists. `/doppel start` initializes the memory store but does not itself merge; `/doppel next` and `/doppel stop` save Markdown journals and JSON snapshots without triggering integration. Integration events log to the server console.

Operator commands:

```mcfunction
/doppel mip status
/doppel mip logs
/doppel mip run
/doppel mip off
/doppel mip on
```

`off` disables automatic integration without deleting individual memories.
`on` persists the setting and integrates immediately when memory storage is
initialized. The toggle persists at `config/doppelganger/mip.enabled`;
if absent, MIP defaults to ON. `run` requires MIP to be ON.

Logs are emitted to the server console and the last 20 messages can be
retrieved using `/doppel mip logs`. This is not a per-event debug log.
The patch is still a structured Markdown integration utility, **not** a live
semantic memory reader: Experimental life AI consumes the structured integrated index. GitHub Actions build has passed; Minecraft runtime testing remains outstanding.


## Daily Minecraft-time schedule

MIP automatically integrates at its configured Minecraft clock time (default **00:00**), provided the Doppel system has been started and MIP is ON. In Minecraft
Java, midnight is **tick 18000**; tick 0 is **06:00**, not midnight.

Set a different time (24-hour Minecraft clock):

```mcfunction
/doppel mip time
/doppel mip time 00:00
/doppel mip time 12:30
/doppel mip status
/doppel mip logs
```

The schedule is saved in `config/doppelganger/mip.time` and survives restarts.
Automatic integration flushes pending individual memory samples first and logs
the outcome to the server console and `/doppel mip logs`.

**Scheduling semantics:** it triggers on normal forward crossings of the
configured clock time; it attempts catch-up after missed clock crossings and time jumps. It runs at most once per underlying Minecraft day number. Rewinding the
clock for the 24 training loops does not trigger the same day again. With the
existing prototype's repeated clock resets, this means the daily merge may
happen once across multiple training loops, not once for each loop. Use
`/doppel mip run` for an immediate merge. Turning MIP OFF suppresses
scheduled merges without deleting memories. `/doppel next` and
`/doppel stop` now flush journals but do not themselves schedule a merge.

## Playable Nao automatic observations and NPC reports

The playable Nao is the primary demonstration source. During an active session, DNMS automatically samples inventory changes and health changes every 20 ticks (about one second), alongside existing movement observations. Positive inventory changes are recorded as `item_gain`, decreases as `item_loss`, and health reductions as `damage` in the currently associated Nao's individual Markdown journal. These are observed changes, **not proof of mining, crafting, combat, or the reason for a change**. The system does not infer emotions or motivations from these events.

- `/doppel logs` — view up to 100 recent in-game report entries, including observed item gains and damage.
- `/doppel npc report` — request each active stand-in NPC's position and movement sample count; results are also added to the report log.
- `/doppel mip logs` — separate MIP integration diagnostics.

Reports are delivered as operator command feedback and server console messages. They are **not yet unsolicited NPC messages**. The report ring buffer resets on server restart and on `/doppel start`; individual Markdown observations persist when flushed. Inventory and health sampling is best-effort: changes that cancel out between samples can be missed. NPCs can perform limited exposed-log harvesting, contextual conversations and social interactions; full mining/building and general learning remain incomplete. Integrated MIP's derived Markdown index is consulted by the experimental life AI when enabled, but not fed back into HabitBrain movement training.

## Automatic Movement Patch (AMP)

The opt-in [Automatic Movement Patch](patches/automatic-movement/README.md) lets the playable Nao select horizontal movement using the **shared Nao personality** and existing movement habit models. Enable with `/doppel automove on`, disable immediately with `/doppel automove off`, inspect `/doppel automove status`, and view periodic choices with `/doppel logs`. Default is OFF. This is movement imitation, not full autonomous Minecraft gameplay. It does not have pathfinding, guaranteed ledge safety, mining, building or combat; test only in a safe area. Implementation guidance: [AMP AGENTS.md](patches/automatic-movement/AGENTS.md). Build/runtime unverified.

### AMP safety update

- **Self-training prevention:** While `/doppel automove on` is active, generated player movement is excluded from `HabitBrain.observe` and the structured movement journal, preventing an automatic movement feedback loop. Other directly observed events (inventory and health) remain available. Manual training resumes after `/doppel automove off`; the previous observation anchor is reset to the player's current position to prevent a false movement sample across the mode switch.
- **Safer movement:** AMP now requires grounded movement, checks the destination player's collision box, verifies support underfoot, and rejects fluid destinations. These are conservative safeguards, **not full pathfinding or guaranteed fall protection**. Avoid testing near cliffs or hazardous terrain until verified.
- Fabric 1.21.1 CI build has passed; runtime testing remains pending.

## JSON recording persistence

The Fabric mod flushes all pending individual Markdown journals **before** writing the JSON snapshot at each save checkpoint. The JSON file stores all recorded Nao movement tracks, current day, selected Nao and free-play state to `config/doppelganger/recordings.json`. The file is written through a temporary file followed by a replacement move to reduce partial writes. `/doppel stop` saves before clearing in-memory data and refuses to stop if the save fails. `/doppel save` forces a checkpoint; `/doppel resume` reloads saved tracks and restores each Nao's learned model from its existing Markdown journal. `/doppel start` begins a **new** session and may replace the previous snapshot at the next save; use `resume` to continue an existing session. Snapshots also occur every 100 server ticks during active sessions and on `/doppel next`.

The legacy recordings JSON does not include world blocks or NPC entities. Generation checkpoints additionally contain a player inventory snapshot and Nao identity data, but still do not rewind the world. After resume, ghost stand-ins are recreated from tracks, automatic movement defaults OFF, and movement habits are restored from Markdown (not from a serialized in-memory brain). Crash recovery is limited to the last successful checkpoint. Build and Minecraft runtime are still unverified.

## Signed gameplay observations and shared personality invariant

- Inventory gains are recorded as `event=item_gain detail=score=+N item=...`; observed health loss as `event=damage detail=score=-N health=...`. Inventory losses are recorded as `event=item_loss detail=score=0 count=-N item=...`, because consumption is not necessarily a negative outcome. Scores are annotations when reward learning is OFF; when `/doppel reward on` is enabled, reward entries also affect learned movement choices.
- The Markdown movement parser explicitly ignores `event=` lines lacking `action=`, so reward/damage events cannot become accidental `STAY` training samples.
- AMP mode transitions reset the current Nao brain's observation anchor to avoid training on movement performed by AMP. Manual observations resume after AMP is disabled.
- **Do not split the personality:** playable Nao and all 24 NPC Nao share the same authoritative `nao_personality.md`. Individual journals are experiences, not separate personalities. No change to that invariant is intended.
- Every JSON checkpoint first flushes pending Markdown events. The JSON and Markdown are still separate files and are not a single atomic transaction; a crash between writes can leave a partially advanced Markdown journal. A fully transactional snapshot would require a further versioned manifest/rollback design.

## Checkpoint and recovery corrections

- Periodic JSON checkpoints are paced by **server ticks** (at most once per 100 ticks), not by the training loop counter, which can pause at 24000 and previously cause repeated writes.
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
- Minecraft runtime testing is still pending; do not treat the player-state serializer as a complete lossless Minecraft backup.

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

**Known limitations / deferred work:** NPCs remain armor stands, not full player-like autonomous entities; limited exposed-log harvesting and per-Nao inventory/social data, but no full block placement or pathfinding. NPC position is checkpointed per identity and restored on resume. Goals currently share a simple movement policy, and a shared MIP index is a derived knowledge summary, not a complete synchronized subjective memory. The existing checkpoint/resume atomicity defects and player-state restoration risks documented above remain unresolved. Do not claim the mod is tested or production-ready. Preserve all 24 Nao experience weights equally and do not create per-Nao personality files.


## Documentation consistency audit (2026-11-21)

**The following older statements above are superseded and must not be read as current behavior:**

- Older passages saying that MIP Markdown is *never* consulted by NPC decisions are obsolete when experimental `/doppel life on` is enabled: `SharedMemoryReader` reads the derived `nao_integrated_memory.md` and supplies reason-frequency evidence to `NaoLifeAI.chooseGoal`. This is **not** full semantic memory sharing and does not retrain `HabitBrain`.
- Older passages saying signed item/damage annotations *never* affect actions are obsolete **when** `/doppel reward on` is enabled. With reward learning OFF (the default), the annotations remain non-operative.
- Older passages saying `/doppel resume` never restores inventory or always teleports to the last recorded track are obsolete for a generation with `player.json`: the restore code attempts player position, health, XP and inventory restoration. A legacy recording without a player snapshot follows the old track-position behavior. Neither path rewinds the world.
- The generation manifest description predating life AI omitted `nao_lives.json`. New checkpoints include it, and resume copies it when present. Older generations can lack this optional file.
- The statement that all 24 experiences have identical weights means **each recorded sample has multiplier 1.0**, not that each individual has equal *total* influence irrespective of how many samples they contributed. More prolific journals currently influence the shared movement decision more. If equal influence per Nao is intended, normalize by the number of samples per individual.
- `NaoLifeAI` has goal labels for COLLECT/BUILD/SOCIAL but **limited COLLECT and SOCIAL world actions, but no complete BUILD world action**. The current skill increment is limited to exploration movement; do not describe it as full skill learning.
- **Unresolved code/design risk:** `DoppelgangerMod.resume()` assigns global `memories` and copies live `nao_lives.json` before player restoration and full session activation. Failure can leave partial live/global state. The identity file is also independent of the checkpoint until publish; interrupted saves/resumes need transactional recovery.
- **Unresolved code/design risk:** a newly started session reuses the existing `nao_lives.json` identities rather than resetting them; define whether `/doppel start` means a fresh cohort or a continuation before changing this behavior.
- **Unresolved behavior:** life AI updates its energy and exploration skill based on the chosen movement before collision/boundary checks; blocked movement can still count as exploration. Update only after successful displacement.
- **Unresolved behavior:** the life AI layer is opt-in but its goal/skill decisions are heuristic, not an LLM or a fully autonomous survival agent. The stable UUID belongs to saved Nao application data, not to a persistent Minecraft entity.
- **Unresolved build/runtime:** Java 21/Fabric CI compilation has passed; in-game testing is outstanding for these changes. Do not represent implementation as verified.

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


### Deferred implementation (explicit user decision, 2026-11-21)

**Implement later, not in the current stage:**
1. **Player-build protection / safe harvesting**: distinguish naturally occurring harvestable logs from player-placed building blocks, enforce protected areas or permissions, and prevent destructive harvesting of structures.
2. **Pathfinding to harvest targets**: navigate safely around obstacles to reach logs instead of relying only on nearby-target checks.

These two features are **not implemented** and are deliberately deferred. Do not present them as working or silently add them to the current harvesting patch. The existing limited `NaoWorkPatch` can still remove exposed logs from buildings; use only backed-up test worlds until protection is implemented. Revisit protection first, then pathfinding, when the user requests the deferred work.


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

- **MIP defaults to Minecraft 00:00 (tick 18000), with configurable HH:mm**. Valid custom `mip.time` values are loaded. `/doppel mip time HH:mm` accepts valid 24-hour Minecraft times. Natural midnight crossing invokes the existing memory integration. Manual `/doppel mip run` and enabling MIP can still trigger an immediate integration; these are explicit/manual exceptions. This does **not** change per-record weights: all experiences retain weight 1.0, so prolific Naos can still contribute more records. A time skip over midnight may skip integration, as before.
- **Nao continuity during growth:** `spawnGhosts()` no longer destroys and recreates every existing stand-in when another Nao is added. Living stands retain their entity and current position; missing/removed stands are recreated for the same slot using their last available position. The logical Nao UUID and saved journals are unchanged. Explicit `/doppel stop` still removes the temporary stand-ins and `/doppel resume` recreates them from saved tracks; **permanent entity persistence, independent position checkpoints, and protection from every kind of disappearance are not yet implemented**.
- In-game verification remains pending.


## 共通World HTTP APIの責務分離
DNMS本体の `org.nagineco.doppel.core.DnmsWorldHttpApi` をHTTP認証・入力解析・JSON応答の唯一の実装元とする。DNMS-Worldはハッシュ検証付きで取得し、旧HTTP実装をDNMS-LEGACYコメントとして無効化する。世界固有の生成・座標・同期・記憶ジャーナル準備はAdapterに残す。Minecraft側HTTP公開、DNMS側クライアント、管理権限一本化、CI検証は今後の実装。


## 共通AI移管バッチ1
RewardEstimate、SharedMemorySearch、MemoryDecision の正式実装をDNMS coreに集約。DNMS-Worldの旧実装はDNMS-LEGACYコメントで無効化し、互換Adapterのみ残す。Gradleで3クラスのblob hashを検証して取得する。Minecraft既存RLP・目標選択への統合、世界IDによる危険記憶分離、CI検証は未完了。今後の共通AI変更はDNMS側で実施する。


## 独立起動・接続バッチ
DNMSの standalone はFabric非依存のCLI接続モジュール。Java 21、Gradle、DNMS_WORLD_TOKEN（32文字以上）、DNMS_WORLD_URL（既定 http://127.0.0.1:8080）を使用。Worldを先に起動し、DNMSリポジトリで gradle -p standalone run --args=list または --args="spawn UUID x y z" を実行する。管理モードではWorldのnext/resetを拒否するが、初期24人生成・他のローカル管理は残る。既存DNMS-LEGACYコメントを維持。接続契約テストを追加したが実行結果は未確認。

## 今後の実装（独立起動バッチ残件）
- 両リポジトリのCI・実World接続・24/256人回帰試験。
- 独立コアへの人格・個体記憶・MIP/RLP/意思決定・技能・人物台帳の統合。
- DNMS主導のUUID発行・永続台帳・初期人口・管理権限一本化、World旧管理処理のコメントアウト。
- 移動・採集・建築・保存・巻き戻しAPIと両Adapter実装。
- 接続先選択・設定保存、世界ID、個体転送、障害時の整合性・再接続。
- Minecraft既存AIとの統合、長期負荷試験、無料公開。


## 世界ID・生成要求・個体台帳の連携
DNMS standalone の `next-world root [expected-world-uuid]` は認証APIで世界IDを取得し、世界別 spawn.pending を使用して生成後 population.csv を更新する。旧 next は互換用。世界IDの前後照合は行うが、サーバー側の原子的な世界ID条件付き生成ではない。

### 今後の実装
- 世界ID条件付き生成・生成と保存の一貫性・台帳自動復元・初期24人の管理権限統合。
- 世界保存・巻き戻し制御の共通化、既存セーブ互換性、実接続・回帰・256人負荷テスト。
- CI未確認。共通コアへの移管は独立DNMS実行時の完全統合を意味しない。


### 保存障害対策・チェックポイント共通化
API生成はチェックポイント成功後に応答し、失敗時は変更操作を停止する。同一UUIDの再要求で保存を再試行する。CheckpointPolicy は世代ID・1200tick判定・最新ポインタ更新を担当する。
今後の実装: 初期24人の権限統合、台帳復元、保存失敗の完全ロールバック、電源断耐性、Minecraft側統合、CI・実接続・負荷検証。回帰テスト追加済み、実行未確認。


### 保存整合性改善（2026-11）
Worldの共通17ソースは固定コミットとblobハッシュで検証する。最新世代ポインタを正式な復元基準とし、latest.binは互換用ミラーとする。保存ディレクトリは排他ロックし、初期化失敗時に解放する。既存履歴がある場合のポインタ消失は自動復元せず停止する。
今後の実装: 巻き戻し世代付き生成要求、未完了世代の識別・容量管理、電源断耐性、世界ID復旧・複製、初期人口管理、台帳復元、完全ロールバック、0人対応、認証必須化、回帰・実接続テスト。CI未確認。


### 接続・保存安全性の追加改善
管理APIは世界UUIDヘッダー必須。CLIは世界IDを取得し、旧nextではなくnext-worldを使用する。チェックポイントファイルとポインタのforce、例外時の未公開世代削除、同日MIP再実行拒否、空人口の復元条件とMIPガードを追加。記憶初期化失敗時は停止する。
今後の実装: 巻き戻し世代ID、初期24人の権限移管、完全ロールバック、容量管理、台帳復元、旧UUID移行、世界複製・ID復旧、強制終了時の世代識別、空人口の統合テスト。コンパイル・CI・実接続未検証。


### 時間軸識別
管理APIは X-DNMS-World と X-DNMS-Epoch を要求する。timeline-id.txt を永続化し、巻き戻し前に更新する（失敗した巻き戻しでも旧要求は無効）。CLIの生成保留台帳は world UUID / epoch UUID / spawn.pending に分離する。旧台帳は自動再送しない。今後の実装: 初期24人の権限移管、容量管理、台帳復元、旧UUID移行、世界複製、完全ロールバック、強制終了耐性、実接続・巻き戻し回帰試験。


### 重大障害対策
MIPは作業コピーで実行し成功後に切替。新規保存は incomplete マーカーで未完了状態を識別する。dnms.minFreeBytes（既定268435456）で空き容量を確認し、履歴を自動削除しない。world-id.backup から欠落したIDを復元し、不一致時は停止する。個体台帳は epoch/world UUID 別に保存する。
今後の実装: 完全な原子性とロールバック、ディレクトリ同期、旧世代の検証、容量上限と保全方針、初期24人の権限移管、台帳の検証付き復元、Minecraft互換性、負荷・障害試験。今回のコードは実行未検証。
