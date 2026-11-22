package org.nagineco.doppel.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MotionPoseTest {
    @Test void stationaryPreservesYawAndHasNoSwing() {
        MotionPose.Pose p = MotionPose.calculate(1, 2, 1, 2, 42, 10, 0, true);
        assertEquals(42f, p.yaw());
        assertFalse(p.walking());
        assertEquals(0f, p.legSwing());
    }

    @Test void eastwardMovementFacesOriginalDirection() {
        MotionPose.Pose p = MotionPose.calculate(0, 0, 1, 0, 0, 0, 0, true);
        assertEquals(-90f, p.yaw());
        assertTrue(p.walking());
    }

    @Test void disabledAnimationStillRotatesWithoutSwing() {
        MotionPose.Pose p = MotionPose.calculate(0, 0, 0, 1, 12, 3, 2, false);
        assertEquals(0f, p.yaw());
        assertFalse(p.walking());
        assertEquals(0f, p.armSwing());
    }
}
