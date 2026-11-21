# Player Appearance Patch (PAP) — phase 1

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
