package org.nagineco.doppel.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalMovementPerformanceTest {
    @Test void zeroSkillPreservesExistingMovementSpeed() {
        var zero = new PhysicalSkillSync.Skill(0, 0, 0, 0, 0, 0);
        assertEquals(0.12, PhysicalMovementPerformance.stepDistance(zero), 1e-12);
    }

    @Test void maximumSkillCannotExceedFiftyPercentSpeedBoost() {
        var max = new PhysicalSkillSync.Skill(100, 100, 100, 100, 100, 100);
        assertEquals(0.18, PhysicalMovementPerformance.stepDistance(max), 1e-12);
    }

    @Test void learnedAgilityAndEnduranceIncreaseStride() {
        var engine = new PhysicalSkillLearningEngine(256, PhysicalSkillSync.Mode.FULL, 1);
        double before = PhysicalMovementPerformance.stepDistance(engine.state(255));
        engine.practice(0, 120, 1, 1);
        assertTrue(PhysicalMovementPerformance.stepDistance(engine.state(255)) > before);
    }

    @Test void noDistanceOrInvalidDistanceGrantsNoPractice() {
        assertEquals(0, PhysicalMovementPerformance.practiceMinutes(0));
        assertEquals(0, PhysicalMovementPerformance.practiceMinutes(-1));
        assertEquals(0, PhysicalMovementPerformance.practiceMinutes(Double.NaN));
    }

    @Test void movementTrainingIsBounded() {
        assertTrue(PhysicalMovementPerformance.practiceMinutes(0.12) > 0);
        assertEquals(1, PhysicalMovementPerformance.practiceMinutes(100000));
    }
}
