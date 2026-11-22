package org.nagineco.doppel.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryNearestPolicyTest {
    @Test void choosesNearestMatchingMovingMemory() {
        double[] data = {3,0,0,2,1, 1,0,0,2,1, 0,0,0,1,1};
        assertEquals(1, MemoryNearestPolicy.nearestIndex(data, 3, 2, 0,0,0,16));
    }

    @Test void excludesStayAndExactRadiusBoundary() {
        double[] data = {1,0,0,2,0, 2,0,0,2,1};
        assertEquals(-1, MemoryNearestPolicy.nearestIndex(data, 2, 2, 0,0,0,4));
    }

    @Test void preservesFirstIndexOnDistanceTie() {
        double[] data = {1,0,0,2,1, -1,0,0,2,1};
        assertEquals(0, MemoryNearestPolicy.nearestIndex(data, 2, 2, 0,0,0,9));
    }

    @Test void rejectsInvalidBuffer() {
        assertEquals(-1, MemoryNearestPolicy.nearestIndex(new double[5], 2, 0, 0,0,0,1));
    }
}
