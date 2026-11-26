package org.nagineco.doppel.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.decoration.ArmorStandEntity;

/** Reuses the server's synchronized mannequin pose for the visible player limbs. */
final class NaoPlayerModel extends PlayerEntityModel<ArmorStandEntity> {
    NaoPlayerModel(ModelPart root, boolean slim) { super(root, slim); }

    @Override
    public void setAngles(ArmorStandEntity entity, float limbAngle, float limbDistance,
                          float animationProgress, float headYaw, float headPitch) {
        super.setAngles(entity, 0, 0, animationProgress, headYaw, headPitch);
        leftLeg.pitch = (float)Math.toRadians(entity.getLeftLegRotation().getPitch());
        rightLeg.pitch = (float)Math.toRadians(entity.getRightLegRotation().getPitch());
        leftArm.pitch = (float)Math.toRadians(entity.getLeftArmRotation().getPitch());
        rightArm.pitch = (float)Math.toRadians(entity.getRightArmRotation().getPitch());
        leftArm.roll = (float)Math.toRadians(entity.getLeftArmRotation().getRoll());
        rightArm.roll = (float)Math.toRadians(entity.getRightArmRotation().getRoll());
        leftPants.copyTransform(leftLeg);
        rightPants.copyTransform(rightLeg);
        leftSleeve.copyTransform(leftArm);
        rightSleeve.copyTransform(rightArm);
        jacket.copyTransform(body);
        hat.copyTransform(head);
    }
}
