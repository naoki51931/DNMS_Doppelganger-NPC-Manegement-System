package org.nagineco.doppel;

import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

/** Lightweight imitation learner. Each character learns a frequency table
 * of observed player movement directions, conditioned on day/night.
 * This is not a language-model NPC and does not learn block interactions.
 */
public final class HabitBrain {
    public enum Move { STAY, NORTH, SOUTH, WEST, EAST }
    private final int[][] counts = new int[2][Move.values().length];
    private final List<Vec3d> observations = new ArrayList<>();
    private int samples = 0;

    public void observe(Vec3d position) {
        observations.add(position);
        if (observations.size() < 2) return;
        Vec3d previous = observations.get(observations.size() - 2);
        Vec3d delta = position.subtract(previous);
        Move move = classify(delta);
        int dayPart = ((observations.size() - 1) % 24000) >= 12000 ? 1 : 0;
        counts[dayPart][move.ordinal()]++;
        samples++;
    }

    private static Move classify(Vec3d delta) {
        double x = delta.x, z = delta.z;
        if (Math.abs(x) < 0.06 && Math.abs(z) < 0.06) return Move.STAY;
        if (Math.abs(x) > Math.abs(z)) return x > 0 ? Move.EAST : Move.WEST;
        return z > 0 ? Move.SOUTH : Move.NORTH;
    }

    public Move decide(int dayTick) {
        int[] choices = counts[dayTick >= 12000 ? 1 : 0];
        int best = 0;
        for (int i = 1; i < choices.length; i++) if (choices[i] > choices[best]) best = i;
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
