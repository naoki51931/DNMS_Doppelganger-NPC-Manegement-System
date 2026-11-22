package org.nagineco.doppel.core;

/**
 * Portable nearest-memory policy. Each record occupies five doubles:
 * x, y, z, goal ordinal, movable flag.
 */
public final class MemoryNearestPolicy {
    private MemoryNearestPolicy() {}

    public static int nearestIndex(double[] packed, int count, int goal,
                                   double x, double y, double z, double radiusSquared) {
        if (packed == null || count < 0 || count > packed.length / 5
                || !Double.isFinite(radiusSquared) || radiusSquared < 0) return -1;
        int best = -1;
        double bestDistance = radiusSquared;
        for (int i = 0; i < count; i++) {
            int offset = i * 5;
            if (packed[offset + 3] != goal || packed[offset + 4] == 0) continue;
            double dx = packed[offset] - x;
            double dy = packed[offset + 1] - y;
            double dz = packed[offset + 2] - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }
}
