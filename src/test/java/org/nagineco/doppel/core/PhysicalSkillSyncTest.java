package org.nagineco.doppel.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalSkillSyncTest {
    private static final PhysicalSkillSync.Skill MASTER =
        new PhysicalSkillSync.Skill(90, 80, 70, 95, 85, 75);
    private static final PhysicalSkillSync.Skill BEGINNER =
        new PhysicalSkillSync.Skill(10, 20, 30, 15, 25, 35);

    @Test void individualModeKeepsAllSkillsSeparate() {
        assertEquals(BEGINNER, PhysicalSkillSync.transfer(MASTER, BEGINNER,
            PhysicalSkillSync.Mode.INDIVIDUAL, 1));
    }

    @Test void techniqueModePreservesIndividualPhysicalCapacity() {
        var result = PhysicalSkillSync.transfer(MASTER, BEGINNER,
            PhysicalSkillSync.Mode.TECHNIQUE, 1);
        assertEquals(90, result.technique());
        assertEquals(80, result.coordination());
        assertEquals(70, result.proficiency());
        assertEquals(15, result.strength());
        assertEquals(25, result.agility());
        assertEquals(35, result.endurance());
    }

    @Test void fullModeSynchronizesAllDimensions() {
        assertEquals(MASTER, PhysicalSkillSync.transfer(MASTER, BEGINNER,
            PhysicalSkillSync.Mode.FULL, 1));
    }

    @Test void halfTransferInterpolatesWithoutOvershoot() {
        var result = PhysicalSkillSync.transfer(MASTER, BEGINNER,
            PhysicalSkillSync.Mode.FULL, 0.5);
        assertEquals(50, result.technique());
        assertEquals(55, result.strength());
    }

    @Test void broadcastKeepsSourceAndSupports256Npcs() {
        var all = new java.util.ArrayList<PhysicalSkillSync.Skill>();
        all.add(MASTER);
        for (int i = 1; i < 256; i++) all.add(BEGINNER);
        List<PhysicalSkillSync.Skill> result = PhysicalSkillSync.broadcast(all, 0,
            PhysicalSkillSync.Mode.FULL, 1);
        assertEquals(256, result.size());
        assertEquals(MASTER, result.get(0));
        assertEquals(MASTER, result.get(255));
        assertEquals(BEGINNER, all.get(255));
    }

    @Test void invalidRatiosAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PhysicalSkillSync.transfer(
            MASTER, BEGINNER, PhysicalSkillSync.Mode.FULL, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> PhysicalSkillSync.transfer(
            MASTER, BEGINNER, PhysicalSkillSync.Mode.FULL, 1.5));
    }
}
