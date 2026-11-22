package org.nagineco.doppel.core;

/** Engine-independent NPC movement bounds and motion detection. */
public final class MovementPolicy {
    private MovementPolicy() {}

    /** Preserve the existing 32-block recording-anchor radius. */
    public static boolean withinAnchor(double x, double y, double z,
                                       double anchorX, double anchorY, double anchorZ) {
        double dx = x - anchorX, dy = y - anchorY, dz = z - anchorZ;
        return dx * dx + dy * dy + dz * dz <= 32.0 * 32.0;
    }

    /** World adapters supply their own collision and danger checks. */
    public static boolean canMove(double x, double y, double z,
                                  double anchorX, double anchorY, double anchorZ,
                                  boolean freeSpace, boolean safe) {
        return withinAnchor(x, y, z, anchorX, anchorY, anchorZ) && freeSpace && safe;
    }

    public static boolean moved(double fromX, double fromY, double fromZ,
                                double toX, double toY, double toZ) {
        double dx = toX - fromX, dy = toY - fromY, dz = toZ - fromZ;
        return dx * dx + dy * dy + dz * dz > 0.000001;
    }
}
