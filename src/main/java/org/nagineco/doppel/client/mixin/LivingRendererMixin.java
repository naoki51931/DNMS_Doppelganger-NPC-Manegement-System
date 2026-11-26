package org.nagineco.doppel.client.mixin;
import org.nagineco.doppel.client.NaoRendererAccess;
import org.nagineco.doppel.client.NaoHumanoidRenderer;
import org.nagineco.doppel.client.NaoRenderMetrics;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntityRenderer.class)
public abstract class LivingRendererMixin {
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"), cancellable = true)
    private void dnms$render(LivingEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        if ((Object)this instanceof NaoRendererAccess access && entity instanceof ArmorStandEntity stand && NaoHumanoidRenderer.skinOwner(stand) != null) {
            long started = NaoRenderMetrics.begin();
            access.dnms$render(stand, yaw, delta, matrices, vertices, light);
            NaoRenderMetrics.end(stand.getUuid(), started);
            ci.cancel();
        }
    }
}
