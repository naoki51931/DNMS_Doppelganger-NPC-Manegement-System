package org.nagineco.doppel.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LearnedActionPolicyTest {
    @Test void picksMostObservedActionWithFirstIndexTieBreak() {
        assertEquals(1, LearnedActionPolicy.dominant(new int[]{0, 3, 3, 1}));
    }

    @Test void noPositiveEvidenceMeansStay() {
        assertEquals(0, LearnedActionPolicy.dominantPositive(new double[]{0, -2, 0}));
    }

    @Test void positiveScoresChooseStrongestAction() {
        assertEquals(2, LearnedActionPolicy.dominantPositive(new double[]{0, 1, 5}));
    }

    @Test void clipsRewardAtTwenty() {
        assertEquals(20, LearnedActionPolicy.clippedReward(50));
        assertEquals(-20, LearnedActionPolicy.clippedReward(-50));
    }

    @Test void weighsPersonalityAndOptionalReward() {
        assertEquals(6, LearnedActionPolicy.weightedEvidence(2, 4, 5, true));
        assertEquals(2, LearnedActionPolicy.weightedEvidence(2, 4, 5, false));
        assertEquals(0, LearnedActionPolicy.weightedEvidence(2, 4, 0, true));
    }
}
