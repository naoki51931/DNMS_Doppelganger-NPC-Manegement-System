package org.nagineco.doppel.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcDecisionEngineTest {
    @Test void restingOverridesLearnedMovement() {
        assertEquals(NpcAction.Kind.STAY, NpcDecisionEngine.decide(true,
            new NpcAction(NpcAction.Kind.EAST), 100, 0).kind());
    }

    @Test void learnedMovementTakesPriority() {
        assertEquals(NpcAction.Kind.WEST, NpcDecisionEngine.decide(false,
            new NpcAction(NpcAction.Kind.WEST), 80, 0).kind());
    }

    @Test void fallbackPreservesCardinalCycle() {
        assertEquals(NpcAction.Kind.NORTH, NpcDecisionEngine.decide(false,
            new NpcAction(NpcAction.Kind.STAY), 0, 0).kind());
        assertEquals(NpcAction.Kind.EAST, NpcDecisionEngine.decide(false, null, 80, 0).kind());
        assertEquals(NpcAction.Kind.SOUTH, NpcDecisionEngine.decide(false, null, 160, 0).kind());
        assertEquals(NpcAction.Kind.WEST, NpcDecisionEngine.decide(false, null, 240, 0).kind());
    }

    @Test void negativeTicksUseFloorMod() {
        assertEquals(NpcAction.Kind.WEST, NpcDecisionEngine.decide(false, null, -80, 0).kind());
    }

    @Test void actionRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new NpcAction(null));
    }
}
