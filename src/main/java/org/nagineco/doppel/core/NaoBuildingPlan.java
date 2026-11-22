package org.nagineco.doppel.core;

import java.util.List;

/** Small fixed starter shelter blueprint, independent of Minecraft. */
public final class NaoBuildingPlan {
    private NaoBuildingPlan() {}
    public record Offset(int x, int y, int z) {}
    // Four corner posts around a 3x3 footprint, followed by a lintel.
    private static final List<Offset> STARTER = List.of(
        new Offset(-1, 0, -1), new Offset(1, 0, -1),
        new Offset(-1, 0, 1), new Offset(1, 0, 1),
        new Offset(-1, 1, -1), new Offset(1, 1, -1),
        new Offset(-1, 1, 1), new Offset(1, 1, 1));
    public static List<Offset> starter() { return STARTER; }
    public static int remaining(int completed) {
        if (completed < 0 || completed > STARTER.size()) throw new IllegalArgumentException("completed");
        return STARTER.size() - completed;
    }
    public static Offset next(int completed) {
        if (completed < 0 || completed >= STARTER.size()) throw new IllegalArgumentException("completed");
        return STARTER.get(completed);
    }
}
