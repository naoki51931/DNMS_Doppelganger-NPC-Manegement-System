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

    /** Deep-copy snapshot for deterministic, game-independent persistence. */
    public Snapshot snapshot() {
        int emotions = counts[0].length;
        int motivations = counts[0][0].length;
        int movements = counts[0][0][0].length;
        int[][][][] savedCounts = new int[2][emotions][motivations][movements];
        double[][][][] savedRewards = new double[2][emotions][motivations][movements];
        for (int p = 0; p < 2; p++)
            for (int e = 0; e < emotions; e++)
                for (int r = 0; r < motivations; r++) {
                    System.arraycopy(counts[p][e][r], 0, savedCounts[p][e][r], 0, movements);
                    System.arraycopy(rewards[p][e][r], 0, savedRewards[p][e][r], 0, movements);
                }
        return new Snapshot(savedCounts, savedRewards, emotion, motivation, samples);
    }

    public record Snapshot(int[][][][] counts, double[][][][] rewards,
                           int emotion, int motivation, int samples) {}

    /** Restore validated data without replaying journals (which would double-count). */
    public static NpcLearningState restore(Snapshot snapshot) {
        if (snapshot == null || snapshot.counts() == null || snapshot.rewards() == null
                || snapshot.counts().length != 2 || snapshot.rewards().length != 2)
            throw new IllegalArgumentException("Invalid learning snapshot");
        int[][][][] c = snapshot.counts();
        double[][][][] r = snapshot.rewards();
        if (c[0] == null || c[0].length == 0 || c[0][0] == null
                || c[0][0].length == 0 || c[0][0][0] == null || c[0][0][0].length == 0)
            throw new IllegalArgumentException("Invalid learning dimensions");
        int emotions = c[0].length, motivations = c[0][0].length, movements = c[0][0][0].length;
        if (emotions > 64 || motivations > 64 || movements > 64 || snapshot.samples() < 0
                || snapshot.emotion() < 0 || snapshot.emotion() >= emotions
                || snapshot.motivation() < 0 || snapshot.motivation() >= motivations)
            throw new IllegalArgumentException("Invalid learning metadata");
        NpcLearningState state = new NpcLearningState(emotions, motivations, movements);
        long observed = 0;
        for (int p = 0; p < 2; p++) {
            if (c[p] == null || r[p] == null || c[p].length != emotions || r[p].length != emotions)
                throw new IllegalArgumentException("Invalid learning array shape");
            for (int e = 0; e < emotions; e++) {
                if (c[p][e] == null || r[p][e] == null
                        || c[p][e].length != motivations || r[p][e].length != motivations)
                    throw new IllegalArgumentException("Invalid learning array shape");
                for (int m = 0; m < motivations; m++) {
                    if (c[p][e][m] == null || r[p][e][m] == null
                            || c[p][e][m].length != movements || r[p][e][m].length != movements)
                        throw new IllegalArgumentException("Invalid learning array shape");
                    for (int a = 0; a < movements; a++) {
                        int count = c[p][e][m][a];
                        double reward = r[p][e][m][a];
                        if (count < 0 || !Double.isFinite(reward))
                            throw new IllegalArgumentException("Invalid learning evidence");
                        observed += count;
                        state.counts[p][e][m][a] = count;
                        state.rewards[p][e][m][a] = reward;
                    }
                }
            }
        }
        if (observed != snapshot.samples())
            throw new IllegalArgumentException("Inconsistent sample count");
        state.emotion = snapshot.emotion();
        state.motivation = snapshot.motivation();
        state.samples = snapshot.samples();
        return state;
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) total += value;
        return total;
    }
}
