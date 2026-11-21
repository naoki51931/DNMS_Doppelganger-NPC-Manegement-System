package org.nagineco.doppel;

import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-character imitation model.
 * Explicit emotion/reason labels are self-reports, never inferred as facts.
 * A learned action policy is conditioned on daylight, emotion and motivation.
 */
public final class HabitBrain {
    public enum Move { STAY, NORTH, SOUTH, WEST, EAST }
    public enum Emotion { NEUTRAL, HAPPY, CURIOUS, AFRAID, ANGRY, SAD }
    public enum Reason { NONE, EXPLORE, BUILD, COLLECT, SAFETY, SOCIAL }

    private final int[][][][] counts =
        new int[2][Emotion.values().length][Reason.values().length][Move.values().length];
    private final List<Vec3d> observations = new ArrayList<>();
    private Emotion emotion = Emotion.NEUTRAL;
    private Reason reason = Reason.NONE;
    private int samples = 0;
    private final double[][][][] rewards =
        new double[2][Emotion.values().length][Reason.values().length][Move.values().length];
    private Move lastManualMove = null;
    private Emotion lastManualEmotion = Emotion.NEUTRAL;
    private Reason lastManualReason = Reason.NONE;
    private int lastManualDayPart = 0;

    public void setEmotion(Emotion value) { emotion = value; }
    public void setReason(Reason value) { reason = value; }
    public Emotion emotion() { return emotion; }
    public Reason reason() { return reason; }

    /** Reset movement continuity after AI control or teleport, without resetting learned counts. */
    public void resetObservationAnchor(Vec3d position) {
        observations.clear();
        observations.add(position);
        lastManualMove = null;
    }

    public void observe(Vec3d position) {
        observations.add(position);
        if (observations.size() < 2) return;
        Vec3d previous = observations.get(observations.size() - 2);
        Move move = classify(position.subtract(previous));
        int dayPart = ((observations.size() - 1) % 24000) >= 12000 ? 1 : 0;
        counts[dayPart][emotion.ordinal()][reason.ordinal()][move.ordinal()]++;
        samples++;
        lastManualMove = move;
        lastManualEmotion = emotion;
        lastManualReason = reason;
        lastManualDayPart = dayPart;
    }

    /** Reward only the last manually observed movement, never an AMP-generated move. */
    public void rewardLatestMove(int tick, double value) {
        if (lastManualMove == null || !Double.isFinite(value)) return;
        learnReward(lastManualMove, lastManualEmotion, lastManualReason, lastManualDayPart, value);
    }

    public void learnReward(Move move, Emotion e, Reason r, int part, double value) {
        if (Double.isFinite(value)) rewards[part][e.ordinal()][r.ordinal()][move.ordinal()] += Math.max(-20, Math.min(20, value));
    }

    public Move lastManualMove() { return lastManualMove; }
    public Emotion lastManualEmotion() { return lastManualEmotion; }
    public Reason lastManualReason() { return lastManualReason; }
    public int lastManualDayPart() { return lastManualDayPart; }

    private static Move classify(Vec3d delta) {
        double x = delta.x, z = delta.z;
        if (Math.abs(x) < 0.06 && Math.abs(z) < 0.06) return Move.STAY;
        if (Math.abs(x) > Math.abs(z)) return x > 0 ? Move.EAST : Move.WEST;
        return z > 0 ? Move.SOUTH : Move.NORTH;
    }

    private int[] distribution(int dayPart, Emotion e, Reason r) {
        return counts[dayPart][e.ordinal()][r.ordinal()];
    }

    private static int sum(int[] values) {
        int n = 0;
        for (int v : values) n += v;
        return n;
    }

    /** Exact emotional context first, then a fallback across known contexts. */
    public Move decide(int dayTick) {
        int part = dayTick >= 12000 ? 1 : 0;
        int[] values = distribution(part, emotion, reason);
        if (sum(values) == 0) {
            int[] fallback = new int[Move.values().length];
            for (Emotion e : Emotion.values())
                for (Reason r : Reason.values())
                    for (int i = 0; i < fallback.length; i++)
                        fallback[i] += counts[part][e.ordinal()][r.ordinal()][i];
            values = fallback;
        }
        if (sum(values) == 0) return Move.STAY;
        int best = 0;
        for (int i = 1; i < values.length; i++)
            if (values[i] > values[best]) best = i;
        return Move.values()[best];
    }

    /**
     * One shared personality scores the combined demonstrations of all 24 Nao.
     * Individual memory gives the current character extra influence, while the
     * shared profile controls motivation affinity. No separate personality exists.
     */
    public static Move sharedDecision(List<HabitBrain> all, int characterIndex,
                                      int dayTick, PersonalityMemoryStore personality) {
        return sharedDecision(all, characterIndex, dayTick, personality, false);
    }

    public static Move sharedDecision(List<HabitBrain> all, int characterIndex,
                                      int dayTick, PersonalityMemoryStore personality,
                                      boolean rewardEnabled) {
        if (all.isEmpty()) return Move.STAY;
        int part = dayTick >= 12000 ? 1 : 0;
        double[] scores = new double[Move.values().length];
        for (int j = 0; j < all.size(); j++) {
            HabitBrain brain = all.get(j);
            double individualBoost = 1.0; // All 24 Nao experiences have equal weight.
            for (Reason reason : Reason.values()) {
                // A preference of 0 suppresses the action evidence for this reason.
                double affinity = personality.preference(reason) / 5.0;
                for (Emotion emotion : Emotion.values()) {
                    int[] observed = brain.counts[part][emotion.ordinal()][reason.ordinal()];
                    for (int m = 0; m < scores.length; m++) {
                        scores[m] += (observed[m] + (rewardEnabled ? brain.rewards[part][emotion.ordinal()][reason.ordinal()][m] : 0)) * affinity * individualBoost;
                    }
                }
            }
        }
        int best = 0;
        for (int m = 1; m < scores.length; m++)
            if (scores[m] > scores[best]) best = m;
        return scores[best] > 0 ? Move.values()[best] : Move.STAY;
    }

    public static Vec3d applyMove(Vec3d position, Move move) {
        double distance = 0.12;
        return switch (move) {
            case NORTH -> position.add(0, 0, -distance);
            case SOUTH -> position.add(0, 0, distance);
            case WEST -> position.add(-distance, 0, 0);
            case EAST -> position.add(distance, 0, 0);
            default -> position;
        };
    }

    public void learnMemory(Move move, Emotion e, Reason r, int tick) {
        counts[tick >= 12000 ? 1 : 0][e.ordinal()][r.ordinal()][move.ordinal()]++;
        samples++;
    }

    public Move latestMove() {
        if (observations.size() < 2) return Move.STAY;
        return classify(observations.get(observations.size() - 1)
                .subtract(observations.get(observations.size() - 2)));
    }

    public Vec3d step(Vec3d position, int dayTick) {
        double distance = 0.12;
        return switch (decide(dayTick)) {
            case NORTH -> position.add(0, 0, -distance);
            case SOUTH -> position.add(0, 0, distance);
            case WEST -> position.add(-distance, 0, 0);
            case EAST -> position.add(distance, 0, 0);
            default -> position;
        };
    }

    public int samples() { return samples; }
}
