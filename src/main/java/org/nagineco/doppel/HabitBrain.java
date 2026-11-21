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

    public void setEmotion(Emotion value) { emotion = value; }
    public void setReason(Reason value) { reason = value; }
    public Emotion emotion() { return emotion; }
    public Reason reason() { return reason; }

    public void observe(Vec3d position) {
        observations.add(position);
        if (observations.size() < 2) return;
        Vec3d previous = observations.get(observations.size() - 2);
        Move move = classify(position.subtract(previous));
        int dayPart = ((observations.size() - 1) % 24000) >= 12000 ? 1 : 0;
        counts[dayPart][emotion.ordinal()][reason.ordinal()][move.ordinal()]++;
        samples++;
    }

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
