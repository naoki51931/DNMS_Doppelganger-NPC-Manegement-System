package org.nagineco.doppel.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NaoBuildingPlanTest {
    @Test void blueprintHasEightUniqueSteps() {
        assertEquals(8, NaoBuildingPlan.starter().size());
        assertEquals(8, new java.util.HashSet<>(NaoBuildingPlan.starter()).size());
    }
    @Test void progressNeverExceedsPlan() {
        assertEquals(8, NaoBuildingPlan.remaining(0));
        assertEquals(4, NaoBuildingPlan.remaining(4));
        assertEquals(0, NaoBuildingPlan.remaining(8));
    }
    @Test void stepsAreDeterministic() {
        assertEquals(new NaoBuildingPlan.Offset(-1, 0, -1), NaoBuildingPlan.next(0));
        assertEquals(new NaoBuildingPlan.Offset(1, 1, 1), NaoBuildingPlan.next(7));
    }
    @Test void invalidStepsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> NaoBuildingPlan.next(-1));
        assertThrows(IllegalArgumentException.class, () -> NaoBuildingPlan.next(8));
        assertThrows(IllegalArgumentException.class, () -> NaoBuildingPlan.remaining(9));
    }
}
