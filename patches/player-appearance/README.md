# Player Appearance Patch (PAP) — phase 1

<!-- DNMS_PATCH_BILINGUAL_CURRENT_BEGIN -->

## 日本語ガイド（現行仕様）
PAPは24人の奈央の見た目と識別情報を管理するパッチです。各奈央のUUIDと `skinKey` を `nao_lives.json` に保存します。`/doppel appearance status <slot>` で確認し、`/doppel appearance set <slot> <skinKey>` で設定できます。現在のNPCは防具立てにプレイヤーヘッドと革装備を付けた簡易マネキンで、腕・脚の簡易アニメーションがあります。**本物のプレイヤー型モデル、スキンのダウンロード・適用、完全なプレイヤーエンティティは未実装**です。UUIDは再生成のたびに変更しないこと。将来の描画方式はFabric 1.21.1で検証が必要です。

## English Guide (Current Behavior)
PAP manages identity and appearance metadata for up to 24 Naos. Stable UUIDs and `skinKey` values persist in `nao_lives.json`. Inspect with `/doppel appearance status <slot>` and change metadata with `/doppel appearance set <slot> <skinKey>`. Current NPCs are armor-stand mannequins wearing generic player heads and leather gear, with simple limb animation. **Actual humanoid player rendering, skin download/application and real player entities are not implemented.** Never regenerate logical UUIDs on respawn. Future rendering requires Fabric 1.21.1 compatibility testing.

<!-- DNMS_PATCH_BILINGUAL_CURRENT_END -->


## Phase 4 vanilla visual preview (2026-11-21)

`spawnGhosts()` now equips each existing armor-stand stand-in with a generic player-head item, leather chestplate, leggings and boots, and enables visible arms. This gives a basic human-like **mannequin preview** with no client mod. The saved `skinKey` is not yet applied to the head or to a player skin. This does not meet the requirement for 24 genuine player-shaped NPCs. Minecraft/Fabric build and runtime have not been verified.

## Phase 3 commands (2026-11-21)

During an initialized session, operators can inspect a logical Nao's appearance metadata with `/doppel appearance status <slot>`. The playable Nao owner can change the persisted skin key with `/doppel appearance set <slot> <skinKey>`. Valid slots are 1–24 but must already exist in `NaoLifeStore`. A save failure restores the prior in-memory key. These commands **do not change the visible armor stand or download a skin**.

## Phase 2 integration (2026-11-21)

- `build.gradle` now compiles the PAP source directory.
- `NaoLifeStore.Nao` persists a `skinKey` field in `nao_lives.json`; missing keys from older saves default to `nao/default`.
- `NaoLifeStore.load()` validates UUID syntax and appearance keys; `appearanceRegistry()` constructs a validated 24-slot registry using existing logical UUIDs.
- `DoppelgangerMod.initializeLife()` validates that registry before saving.
- This **does not** replace armor stands or implement player-model rendering. Java 21 / Fabric build and runtime remain unverified.

## Current implementation
`NaoAppearanceRegistry` tracks 1–24 stable logical NPC UUIDs, a validated skin reference key, and active state. It rejects duplicate slots/UUIDs and restores snapshots atomically. The source is plain Java 21 and intentionally independent of Fabric/Yarn mapping changes.

**Not implemented yet:** visible player-model rendering, skin download, client packets, real player entities, animation, Fabric command integration, save-to-disk integration, or replacing the existing armor stands. This is a preparatory identity/appearance component only.

## Next integration tasks
1. Reconcile each logical UUID with existing `NaoLifeStore` UUIDs. Never create new UUIDs on every respawn; migrate old data before activation.
2. Choose a Fabric 1.21.1-compatible humanoid entity renderer or explicitly require a client mod; server-only code cannot promise arbitrary client-side player models without compatible client support.
3. Spawn and despawn entities through the server tick lifecycle; map `slot -> entity UUID` separately from logical UUID.
4. Persist the registry in generation checkpoints, including skin identifiers. Keep skins opt-in and validate permissions and authenticity.
5. Test 1, 2, and 24 NPCs; chunk unload, logout, world restart, world change, and the `/doppel next` rewind.
6. Confirm Java 21 / Fabric 1.21.1 build and live multiplayer visuals before declaring PAP complete.

The current project still uses armor stands. This patch must not be advertised as a player-shaped NPC implementation.
