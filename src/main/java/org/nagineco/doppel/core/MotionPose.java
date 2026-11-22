package org.nagineco.doppel.core;

/**
 * Engine-independent animation math. A renderer decides how to apply this pose.
 */
public final class MotionPose {
    public record Pose(float yaw, float legSwing, float armSwing, boolean walking) {}

    private MotionPose() {}

    public static Pose calculate(double fromX, double fromZ, double toX, double toZ,
                                 float currentYaw, long tick, int slot, boolean enabled) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double horizontalDistanceSq = dx * dx + dz * dz;
        boolean walking = enabled && horizontalDistanceSq > 0.000001;
        float yaw = horizontalDistanceSq > 0.000001
            ? (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0)
            : currentYaw;
        float swing = walking ? (float) Math.sin(tick * 0.35 + slot * 0.7) * 24.0f : 0.0f;
        return new Pose(yaw, swing, swing * 0.65f, walking);
    }
}
