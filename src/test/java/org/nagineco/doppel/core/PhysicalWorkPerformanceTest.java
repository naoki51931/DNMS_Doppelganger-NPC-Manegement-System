package org.nagineco.doppel.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalWorkPerformanceTest {
    @Test void collectionProbabilityIncreasesWithProficiency() {
        var zero = new PhysicalSkillSync.Skill(0, 0, 0, 0, 0, 0);
        var max = new PhysicalSkillSync.Skill(100, 100, 100, 100, 100, 100);
        assertEquals(0.70, PhysicalWorkPerformance.collectionSuccessProbability(zero), 1e-12);
        assertEquals(0.95, PhysicalWorkPerformance.collectionSuccessProbability(max), 1e-12);
    }

    @Test void fullSynchronizationImprovesOtherNaosWorkPerformance() {
        var engine = new PhysicalSkillLearningEngine(256, PhysicalSkillSync.Mode.FULL, 1);
        double before = PhysicalWorkPerformance.collectionSuccessProbability(engine.state(255));
        engine.practice(0, 120, 1, 0.5);
        assertTrue(PhysicalWorkPerformance.collectionSuccessProbability(engine.state(255)) > before);
    }

    @Test void identicalAttemptAlwaysHasIdenticalOutcome() {
        var skill = new PhysicalSkillSync.Skill(50, 50, 50, 50, 50, 50);
        assertEquals(PhysicalWorkPerformance.canExecuteCollection(skill, 3, 123),
            PhysicalWorkPerformance.canExecuteCollection(skill, 3, 123));
    }

    @Test void strongerSkillsNeverTurnSuccessIntoFailureForSameTrial() {
        var weak = new PhysicalSkillSync.Skill(0, 0, 0, 0, 0, 0);
        var strong = new PhysicalSkillSync.Skill(100, 100, 100, 100, 100, 100);
        for (int attempt = 0; attempt < 1000; attempt++) {
            if (PhysicalWorkPerformance.canExecuteCollection(weak, 2, attempt))
                assertTrue(PhysicalWorkPerformance.canExecuteCollection(strong, 2, attempt));
        }
    }

    @Test void rejectsInvalidNpcOrAttempt() {
        assertThrows(IllegalArgumentException.class,
            () -> PhysicalWorkPerformance.canExecuteCollection(null, -1, 0));
        assertThrows(IllegalArgumentException.class,
            () -> PhysicalWorkPerformance.canExecuteCollection(null, 0, -1));
    }
}
