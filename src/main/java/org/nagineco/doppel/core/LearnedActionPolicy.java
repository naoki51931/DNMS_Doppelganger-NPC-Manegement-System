package org.nagineco.doppel.core;

/**
 * Pure policy for interpreting learned movement evidence.
 * Indices are supplied by adapters; this class has no Minecraft dependency.
 */
public final class LearnedActionPolicy {
    private LearnedActionPolicy() {}

    public static int dominant(int[] counts) {
        if (counts == null || counts.length == 0) return 0;
        int best = 0;
        for (int i = 1; i < counts.length; i++)
            if (counts[i] > counts[best]) best = i;
        return best;
    }

    /** Returns 0 (STAY) unless a positive weighted action score exists. */
    public static int dominantPositive(double[] scores) {
        if (scores == null || scores.length == 0) return 0;
        int best = 0;
        for (int i = 1; i < scores.length; i++)
            if (scores[i] > scores[best]) best = i;
        return scores[best] > 0 ? best : 0;
    }

    /** Preserve existing per-observation reward clipping. */
    public static double clippedReward(double reward) {
        return Math.max(-20.0, Math.min(20.0, reward));
    }

    /** Combine observed action count, optional reward and personality affinity. */
    public static double weightedEvidence(int observed, double reward,
                                          double preference, boolean rewardEnabled) {
        return (observed + (rewardEnabled ? reward : 0.0)) * (preference / 5.0);
    }
}
