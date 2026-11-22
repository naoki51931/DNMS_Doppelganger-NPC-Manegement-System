package org.nagineco.doppel.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MovementPolicyTest {
    @Test void boundaryIsInclusive() {
        assertTrue(MovementPolicy.withinAnchor(32, 0, 0, 0, 0, 0));
        assertFalse(MovementPolicy.withinAnchor(32.01, 0, 0, 0, 0, 0));
    }

    @Test void requiresFreeAndSafeWorldConditions() {
        assertTrue(MovementPolicy.canMove(1, 0, 0, 0, 0, 0, true, true));
        assertFalse(MovementPolicy.canMove(1, 0, 0, 0, 0, 0, false, true));
        assertFalse(MovementPolicy.canMove(1, 0, 0, 0, 0, 0, true, false));
    }

    @Test void movementThresholdMatchesOriginal() {
        assertFalse(MovementPolicy.moved(0, 0, 0, 0.001, 0, 0));
        assertTrue(MovementPolicy.moved(0, 0, 0, 0.002, 0, 0));
    }
}
