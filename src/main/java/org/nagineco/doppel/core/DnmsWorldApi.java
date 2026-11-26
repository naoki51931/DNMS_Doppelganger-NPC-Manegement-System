package org.nagineco.doppel.core;

import java.util.UUID;
import java.util.List;

/** Engine-neutral contract. DNMS owns identity; engines own physical state.
 * Coordinates are engine-local. Unsupported operations must not report success.
 */
public interface DnmsWorldApi {
    record Position(int x, int y, int z) {}
    record Npc(UUID id, Position position, double health) {}
    enum SpawnResult { CREATED, ALREADY_EXISTS, CAPACITY, INVALID_POSITION, UNSUPPORTED }
    SpawnResult spawnNpc(UUID id, Position position);
    List<Npc> npcs();
    long worldTick();
}
