# Player Appearance Patch (PAP) — phase 1

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
