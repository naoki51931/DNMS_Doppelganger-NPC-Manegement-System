# DNMS core separation — phase 1

This refactor separates **pure Java logic** from the Fabric-specific entry point without changing the existing commands or gameplay.

- `org.nagineco.doppel.core.MinecraftClock`: pure Minecraft tick / HH:mm conversion; no Fabric API.
- `org.nagineco.doppel.core.AtomicTextFile`: game-independent atomic UTF-8 persistence.
- `DoppelgangerMod`: still owns Minecraft event registration, commands, entities and adapters; it delegates these two operations to the core classes.
- `ConsentStore` remains independent of Minecraft but is **not wired** into NPC spawning yet.

## Next extraction steps

1. Move pure NPC identity, goal and decision logic behind interfaces, keeping Fabric entities in the Minecraft adapter.
2. Extract memory indexing and MIP policy without moving filesystem paths or world-specific time handling into the core.
3. Add unit tests for core logic, then regression-test Minecraft commands, saves and unattended restart.
4. Only consider a native C++ backend after profiling; Java remains the reference implementation.

This is an incremental separation, **not** a full architecture migration. CI and Minecraft runtime tests have not been verified.
