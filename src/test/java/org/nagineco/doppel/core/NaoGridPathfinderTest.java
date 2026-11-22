package org.nagineco.doppel.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NaoGridPathfinderTest {
    @Test void detoursAroundWall() {
        var start = new NaoGridPathfinder.Cell(0, 0);
        var goal = new NaoGridPathfinder.Cell(4, 0);
        var path = NaoGridPathfinder.find(start, goal,
            (x,z) -> !(x == 2 && z >= -1 && z <= 1), 8);
        assertFalse(path.isEmpty());
        assertEquals(goal, path.get(path.size()-1));
        assertTrue(path.stream().noneMatch(c -> c.x() == 2 && Math.abs(c.z()) <= 1));
    }
    @Test void impossibleRouteReturnsEmpty() {
        assertTrue(NaoGridPathfinder.find(new NaoGridPathfinder.Cell(0,0),
            new NaoGridPathfinder.Cell(2,0), (x,z) -> x == 0, 4).isEmpty());
    }
    @Test void repeatedSearchIsDeterministic() {
        var a = new NaoGridPathfinder.Cell(0,0);
        var b = new NaoGridPathfinder.Cell(3,3);
        assertEquals(NaoGridPathfinder.find(a,b,(x,z)->true,8),
            NaoGridPathfinder.find(a,b,(x,z)->true,8));
    }
    @Test void respectsSearchRadius() {
        assertTrue(NaoGridPathfinder.find(new NaoGridPathfinder.Cell(0,0),
            new NaoGridPathfinder.Cell(50,0),(x,z)->true,8).isEmpty());
    }
    @Test void rejectsInvalidRadius() {
        assertThrows(IllegalArgumentException.class, () ->
            NaoGridPathfinder.find(new NaoGridPathfinder.Cell(0,0),
                new NaoGridPathfinder.Cell(1,0),(x,z)->true,0));
    }
}
