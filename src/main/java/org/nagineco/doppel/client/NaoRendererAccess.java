package org.nagineco.doppel.client;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.ArmorStandEntity;
public interface NaoRendererAccess {
    void dnms$render(ArmorStandEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light);
}
