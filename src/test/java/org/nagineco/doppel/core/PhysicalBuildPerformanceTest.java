package org.nagineco.doppel.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalBuildPerformanceTest {
    @Test void probabilityIsBoundedByProficiency() {
        var zero = new PhysicalSkillSync.Skill(0, 0, 0, 0, 0, 0);
        var max = new PhysicalSkillSync.Skill(100, 100, 100, 100, 100, 100);
        assertEquals(0.65, PhysicalBuildPerformance.successProbability(zero), 1e-12);
        assertEquals(0.95, PhysicalBuildPerformance.successProbability(max), 1e-12);
    }

    @Test void fullModeSharesConstructionProficiency() {
        var engine = new PhysicalSkillLearningEngine(256, PhysicalSkillSync.Mode.FULL, 1);
        double before = PhysicalBuildPerformance.successProbability(engine.state(255));
        engine.practice(0, 120, 1, 0.5);
        assertTrue(PhysicalBuildPerformance.successProbability(engine.state(255)) > before);
    }

    @Test void repeatedAttemptIsDeterministic() {
        var skill = new PhysicalSkillSync.Skill(40, 40, 40, 40, 40, 40);
        assertEquals(PhysicalBuildPerformance.canBuild(skill, 1, 30),
            PhysicalBuildPerformance.canBuild(skill, 1, 30));
    }

    @Test void higherSkillNeverLosesPreviouslySuccessfulTrial() {
        var weak = new PhysicalSkillSync.Skill(0, 0, 0, 0, 0, 0);
        var strong = new PhysicalSkillSync.Skill(100, 100, 100, 100, 100, 100);
        for (int i = 0; i < 1000; i++)
            if (PhysicalBuildPerformance.canBuild(weak, 0, i))
                assertTrue(PhysicalBuildPerformance.canBuild(strong, 0, i));
    }

    @Test void invalidAttemptsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PhysicalBuildPerformance.canBuild(null, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> PhysicalBuildPerformance.canBuild(null, 0, -1));
    }
}
