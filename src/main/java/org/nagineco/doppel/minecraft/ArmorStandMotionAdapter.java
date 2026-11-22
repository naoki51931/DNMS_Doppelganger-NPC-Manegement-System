package org.nagineco.doppel.minecraft;

import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.EulerAngle;
import net.minecraft.util.math.Vec3d;
import org.nagineco.doppel.core.MotionPose;

/** Minecraft-only adapter: applies a core pose to the vanilla armor stand. */
public final class ArmorStandMotionAdapter {
    private ArmorStandMotionAdapter() {}

    public static void apply(ArmorStandEntity ghost, Vec3d previous, Vec3d position,
                             long tick, int slot, boolean animate) {
        MotionPose.Pose pose = MotionPose.calculate(
            previous.x, previous.z, position.x, position.z,
            ghost.getYaw(), tick, slot, animate);
        ghost.refreshPositionAndAngles(position.x, position.y, position.z, pose.yaw(), 0);
        if (pose.walking()) {
            float swing = pose.legSwing();
            ghost.setLeftLegRotation(new EulerAngle(swing, 0, 0));
            ghost.setRightLegRotation(new EulerAngle(-swing, 0, 0));
            ghost.setLeftArmRotation(new EulerAngle(-pose.armSwing(), 0, -5));
            ghost.setRightArmRotation(new EulerAngle(pose.armSwing(), 0, 5));
        } else {
            ghost.setLeftLegRotation(new EulerAngle(0, 0, 0));
            ghost.setRightLegRotation(new EulerAngle(0, 0, 0));
            ghost.setLeftArmRotation(new EulerAngle(0, 0, -5));
            ghost.setRightArmRotation(new EulerAngle(0, 0, 5));
        }
    }
}
