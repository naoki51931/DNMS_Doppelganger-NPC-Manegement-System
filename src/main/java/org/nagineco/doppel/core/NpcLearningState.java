package org.nagineco.doppel.core;

/**
 * Minecraft-independent learned evidence for a single NPC.
 * Array dimensions: day part, emotion, motivation, movement.
 */
public final class NpcLearningState {
    private final int[][][][] counts;
    private final double[][][][] rewards;
    private int emotion;
    private int motivation;
    private int samples;

    public NpcLearningState(int emotions, int motivations, int movements) {
        if (emotions <= 0 || motivations <= 0 || movements <= 0)
            throw new IllegalArgumentException("Learning dimensions must be positive");
        counts = new int[2][emotions][motivations][movements];
        rewards = new double[2][emotions][motivations][movements];
    }

    public void setEmotion(int value) {
        if (value < 0 || value >= counts[0].length) throw new IllegalArgumentException("emotion");
        emotion = value;
    }

    public void setMotivation(int value) {
        if (value < 0 || value >= counts[0][0].length) throw new IllegalArgumentException("motivation");
        motivation = value;
    }

    public int emotion() { return emotion; }
    public int motivation() { return motivation; }
    public int samples() { return samples; }

    public void observe(int part, int emotion, int motivation, int movement) {
        counts[part][emotion][motivation][movement]++;
        samples++;
    }

    public void reward(int part, int emotion, int motivation, int movement, double value) {
        if (Double.isFinite(value))
            rewards[part][emotion][motivation][movement] += LearnedActionPolicy.clippedReward(value);
    }

    public int[] distribution(int part, int emotion, int motivation) {
        return counts[part][emotion][motivation].clone();
    }

    public int decide(int part) {
        int[] values = distribution(part, emotion, motivation);
        if (sum(values) == 0) {
            values = new int[counts[part][0][0].length];
            for (int e = 0; e < counts[part].length; e++)
                for (int r = 0; r < counts[part][e].length; r++)
                    for (int m = 0; m < values.length; m++)
                        values[m] += counts[part][e][r][m];
        }
        return sum(values) == 0 ? 0 : LearnedActionPolicy.dominant(values);
    }

    public void accumulateScores(int part, double[] preferences, boolean rewardEnabled,
                                 double[] scores) {
        if (preferences.length != counts[part][0].length ||
                scores.length != counts[part][0][0].length)
            throw new IllegalArgumentException("Incompatible scoring dimensions");
        for (int r = 0; r < preferences.length; r++)
            for (int e = 0; e < counts[part].length; e++)
                for (int m = 0; m < scores.length; m++)
                    scores[m] += LearnedActionPolicy.weightedEvidence(
                        counts[part][e][r][m], rewards[part][e][r][m],
                        preferences[r], rewardEnabled);
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) total += value;
        return total;
    }
}
