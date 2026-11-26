package org.nagineco.doppel;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcSpawnGuardTest {
    @Test void successfulAppend() {
        var bodies = new ArrayList<String>();
        NpcSpawnGuard.register(bodies, 0, "new", () -> true, msg -> fail(msg));
        assertEquals(List.of("new"), bodies);
    }
    @Test void rejectedAppend() {
        var bodies = new ArrayList<>(List.of("existing"));
        var errors = new ArrayList<String>();
        assertThrows(IllegalStateException.class,
            () -> NpcSpawnGuard.register(bodies, 1, "new", () -> false, errors::add));
        assertEquals(List.of("existing"), bodies);
        assertEquals(List.of("NPC body spawn failed for Doppel 2"), errors);
    }
    @Test void rejectedReplacementPreservesSlot() {
        var bodies = new ArrayList<>(List.of("old"));
        assertThrows(IllegalStateException.class,
            () -> NpcSpawnGuard.register(bodies, 0, "new", () -> false, msg -> {}));
        assertEquals(List.of("old"), bodies);
    }
    @Test void successfulReplacement() {
        var bodies = new ArrayList<>(List.of("old", "other"));
        NpcSpawnGuard.register(bodies, 0, "new", () -> true, msg -> fail(msg));
        assertEquals(List.of("new", "other"), bodies);
    }
    @Test void spawnExceptionPreservesList() {
        var bodies = new ArrayList<String>();
        assertThrows(IllegalArgumentException.class, () -> NpcSpawnGuard.register(
            bodies, 0, "new", () -> { throw new IllegalArgumentException(); }, msg -> fail(msg)));
        assertTrue(bodies.isEmpty());
    }
}
