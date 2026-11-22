package org.nagineco.doppel.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcLearningStateTest {
    @Test void startsWithStayAndNoSamples() {
        NpcLearningState state = new NpcLearningState(2, 2, 5);
        assertEquals(0, state.decide(0));
        assertEquals(0, state.samples());
    }

    @Test void currentEmotionAndMotivationTakePriority() {
        NpcLearningState state = new NpcLearningState(2, 2, 5);
        state.observe(0, 0, 0, 1);
        state.observe(0, 1, 1, 2);
        state.observe(0, 1, 1, 2);
        assertEquals(1, state.decide(0));
        state.setEmotion(1);
        state.setMotivation(1);
        assertEquals(2, state.decide(0));
        assertEquals(3, state.samples());
    }

    @Test void fallsBackAcrossContextsWithinSameDayPart() {
        NpcLearningState state = new NpcLearningState(2, 2, 5);
        state.observe(1, 1, 1, 3);
        assertEquals(0, state.decide(0));
        assertEquals(3, state.decide(1));
    }

    @Test void rewardAndPreferencesControlSharedEvidence() {
        NpcLearningState state = new NpcLearningState(2, 2, 5);
        state.observe(0, 0, 1, 2);
        state.reward(0, 0, 1, 2, 100);
        double[] scores = new double[5];
        state.accumulateScores(0, new double[]{0, 5}, true, scores);
        assertEquals(21, scores[2]);
        double[] withoutRewards = new double[5];
        state.accumulateScores(0, new double[]{0, 5}, false, withoutRewards);
        assertEquals(1, withoutRewards[2]);
    }

    @Test void invalidDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new NpcLearningState(0, 1, 5));
    }
}
