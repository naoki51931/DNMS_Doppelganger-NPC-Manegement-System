package org.nagineco.patch.rewardlearning;

import org.nagineco.doppel.HabitBrain;

/**
 * Optional reward learning: associate the latest observed manual movement with
 * item gains (+) or health damage (-). Does not infer causal certainty.
 */
public final class RewardLearningPatch {
    private RewardLearningPatch() {}

    public static void apply(HabitBrain brain, int dayTick, double reward) {
        if (brain == null || !Double.isFinite(reward) || reward == 0) return;
        brain.rewardLatestMove(dayTick, reward);
    }
}
